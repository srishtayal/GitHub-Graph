import json
import unittest

from app.core.exceptions import ExplanationResponseError
from app.services.explanations.evidence_selector import EvidenceSelector
from app.services.explanations.query_router import QueryRouter
from app.services.explanations.response_parser import ResponseParser
from tests.fixtures.phase7_explanations import dependency_request, grounded_dependency_response


class ResponseParserTest(unittest.TestCase):
    def setUp(self) -> None:
        request = dependency_request()
        self.selection = EvidenceSelector().select(request, QueryRouter().route(request.query))

    def test_rejects_an_invented_graph_reference_even_when_other_citations_are_valid(self) -> None:
        body = json.loads(grounded_dependency_response())
        body["referencedNodeIds"].append("function:invented")
        with self.assertRaises(ExplanationResponseError):
            ResponseParser().parse(json.dumps(body), self.selection)

    def test_rejects_an_unsupported_edge_reference(self) -> None:
        body = json.loads(grounded_dependency_response())
        body["referencedEdgeIds"].append("edge:invented")
        with self.assertRaises(ExplanationResponseError):
            ResponseParser().parse(json.dumps(body), self.selection)

    def test_rejects_response_that_cites_only_invented_evidence(self) -> None:
        body = json.loads(grounded_dependency_response())
        body["supportingEvidence"] = [
            {"evidenceId": "made-up", "sourceType": "graph", "rationale": "Not provided."}
        ]
        with self.assertRaises(ExplanationResponseError):
            ResponseParser().parse(json.dumps(body), self.selection)

    def test_rejects_a_known_evidence_id_with_the_wrong_source_type(self) -> None:
        body = json.loads(grounded_dependency_response())
        body["supportingEvidence"][0]["sourceType"] = "graph"
        with self.assertRaises(ExplanationResponseError):
            ResponseParser().parse(json.dumps(body), self.selection)


if __name__ == "__main__":
    unittest.main()
