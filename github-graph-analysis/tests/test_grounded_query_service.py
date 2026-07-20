import unittest

from app.schemas.explanations import ExplanationResponse, GroundedQueryRequest
from app.services.explanations.grounded_query_service import GroundedQueryService
from tests.fixtures.graph_fixtures import primary_graph


class RecordingExplanationService:
    def __init__(self) -> None:
        self.request = None

    def explain(self, request):
        self.request = request
        return ExplanationResponse(
            intent="impact_analysis",
            answer="The bounded impact result identifies affected nodes.",
            supportingEvidence=[
                {
                    "evidenceId": "analytics:impact-analysis",
                    "sourceType": "impactAnalysis",
                    "rationale": "It contains the computed BFS result.",
                }
            ],
            referencedNodeIds=[request.impactAnalysis.queryNodeId]
            if request.impactAnalysis
            else [],
            confidence="high",
        )


def query_request(**updates) -> GroundedQueryRequest:
    values = {
        "repositoryId": "repo-sample",
        "query": "What breaks if handler fails?",
        "graph": primary_graph(),
        "history": [],
        "repositoryMetadata": {"name": "sample"},
        "snapshotMetadata": {
            "repositoryId": "repo-sample",
            "snapshotId": "snapshot-one",
            "branchName": "main",
            "commitSha": "abc123",
        },
    }
    values.update(updates)
    return GroundedQueryRequest(**values)


class GroundedQueryServiceTest(unittest.TestCase):
    def test_infers_target_and_computes_impact_before_explaining(self) -> None:
        explanation = RecordingExplanationService()
        response = GroundedQueryService(explanation_service=explanation).query(query_request())

        self.assertEqual("function:handler", explanation.request.impactAnalysis.queryNodeId)
        self.assertEqual("impact_analysis", response.intent)
        self.assertEqual("snapshot-one", response.snapshotMetadata.snapshotId)
        self.assertEqual("phase7-grounded-v2", response.modelMetadata.promptVersion)

    def test_unknown_target_returns_insufficient_without_model_call(self) -> None:
        explanation = RecordingExplanationService()
        response = GroundedQueryService(explanation_service=explanation).query(
            query_request(targetNodeId="function:missing")
        )

        self.assertEqual("insufficient", response.confidence)
        self.assertIn("does not exist", response.limitations[0])
        self.assertIsNone(explanation.request)

    def test_computes_dependency_trace_for_flow_question(self) -> None:
        explanation = RecordingExplanationService()
        GroundedQueryService(explanation_service=explanation).query(
            query_request(
                query="Trace dependencies from api.py",
                targetNodeId="file:api",
            )
        )

        self.assertEqual("file:api", explanation.request.dependencyTrace.queryNodeId)
        self.assertEqual("DFS", explanation.request.dependencyTrace.traversalType)

    def test_computes_similarity_ranking_for_named_target(self) -> None:
        explanation = RecordingExplanationService()
        GroundedQueryService(explanation_service=explanation).query(
            query_request(
                query="Which functions are similar to handler?",
                targetNodeId="function:handler",
            )
        )

        self.assertEqual("function:handler", explanation.request.similarityRanking.targetNodeId)

    def test_computes_centrality_and_topology_for_matching_intents(self) -> None:
        centrality_explanation = RecordingExplanationService()
        GroundedQueryService(explanation_service=centrality_explanation).query(
            query_request(query="Which functions are most critical?")
        )
        self.assertIsNotNone(centrality_explanation.request.centrality)

        topology_explanation = RecordingExplanationService()
        GroundedQueryService(explanation_service=topology_explanation).query(
            query_request(query="Are there dependency cycles?")
        )
        self.assertIsNotNone(topology_explanation.request.cycleDetection)
        self.assertIsNotNone(topology_explanation.request.topologicalSort)

    def test_question_without_a_recognized_intent_returns_insufficient(self) -> None:
        explanation = RecordingExplanationService()
        response = GroundedQueryService(explanation_service=explanation).query(
            query_request(query="Tell me something interesting")
        )

        self.assertEqual("unknown_or_insufficient", response.intent)
        self.assertEqual("insufficient", response.confidence)

    def test_bug_query_computes_hypothesis_candidates_with_reasons(self) -> None:
        explanation = RecordingExplanationService()
        GroundedQueryService(explanation_service=explanation).query(
            query_request(
                query="Why is this error happening in handler?",
                targetNodeId="function:handler",
                errorLog="RuntimeError: request failed",
            )
        )

        candidates = explanation.request.bugLocalization.suspectedRootCauses
        self.assertTrue(candidates)
        self.assertTrue(candidates[0].reasons)
        self.assertIn(candidates[0].confidence, {"low", "medium", "high"})


if __name__ == "__main__":
    unittest.main()
