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

    def test_accepts_known_evidence_and_drops_unknown_graph_references(self) -> None:
        body = json.loads(grounded_dependency_response())
        body["referencedNodeIds"].append("function:invented")
        response = ResponseParser().parse(json.dumps(body), self.selection)

        self.assertNotIn("function:invented", response.referencedNodeIds)
        self.assertEqual("high", response.confidence)

    def test_rejects_response_that_cites_only_invented_evidence(self) -> None:
        body = json.loads(grounded_dependency_response())
        body["supportingEvidence"] = [
            {"evidenceId": "made-up", "sourceType": "graph", "rationale": "Not provided."}
        ]
        with self.assertRaises(ExplanationResponseError):
            ResponseParser().parse(json.dumps(body), self.selection)


if __name__ == "__main__":
    unittest.main()
