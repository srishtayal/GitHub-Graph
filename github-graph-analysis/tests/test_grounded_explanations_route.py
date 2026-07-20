import unittest

from fastapi.testclient import TestClient

from app.api.routes.explanations import get_grounded_query_service
from app.core.exceptions import ExplanationProviderError
from app.main import app
from app.schemas.explanations import GroundedQueryResponse
from tests.fixtures.graph_fixtures import primary_graph


class FakeGroundedQueryService:
    def __init__(self, result) -> None:
        self.result = result

    def query(self, request):
        if isinstance(self.result, Exception):
            raise self.result
        return self.result


def payload():
    return {
        "repositoryId": "repo-sample",
        "query": "What is the repository structure?",
        "graph": primary_graph().model_dump(mode="json"),
        "snapshotMetadata": {
            "repositoryId": "repo-sample",
            "snapshotId": "snapshot-one",
        },
    }


class GroundedExplanationsRouteTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)

    def tearDown(self) -> None:
        app.dependency_overrides.clear()

    def test_returns_grounded_response_with_provenance(self) -> None:
        result = GroundedQueryResponse(
            intent="repository_structure",
            answer="The graph contains repository files.",
            supportingEvidence=[
                {
                    "evidenceId": "graph:summary",
                    "sourceType": "graph",
                    "rationale": "It reports graph counts.",
                }
            ],
            confidence="high",
            snapshotMetadata={
                "repositoryId": "repo-sample",
                "snapshotId": "snapshot-one",
            },
            modelMetadata={
                "model": "test-model",
                "promptVersion": "test-prompt",
                "orchestrationVersion": "test-orchestration",
            },
        )
        app.dependency_overrides[get_grounded_query_service] = lambda: FakeGroundedQueryService(result)

        response = self.client.post("/internal/v1/explanations/query", json=payload())

        self.assertEqual(200, response.status_code)
        self.assertEqual("snapshot-one", response.json()["snapshotMetadata"]["snapshotId"])
        self.assertEqual("test-prompt", response.json()["modelMetadata"]["promptVersion"])

    def test_maps_provider_failure_to_502(self) -> None:
        app.dependency_overrides[get_grounded_query_service] = lambda: FakeGroundedQueryService(
            ExplanationProviderError("failed")
        )

        response = self.client.post("/internal/v1/explanations/query", json=payload())

        self.assertEqual(502, response.status_code)


if __name__ == "__main__":
    unittest.main()
