import unittest

from app.services.explanations.evidence_selector import EvidenceSelector
from app.services.explanations.query_router import QueryRouter
from tests.fixtures.phase7_explanations import dependency_request


class EvidenceSelectorTest(unittest.TestCase):
    def test_selects_trace_and_referenced_graph_entities_only(self) -> None:
        request = dependency_request()
        selection = EvidenceSelector().select(request, QueryRouter().route(request.query))

        self.assertTrue(selection.sufficient)
        self.assertIn("analytics:dependency-trace", {item.evidence_id for item in selection.items})
        self.assertIn("file:api", selection.allowed_node_ids)
        self.assertNotIn("file:cycle-a", selection.allowed_node_ids)
        self.assertIn("edge:01", selection.allowed_edge_ids)
        self.assertNotIn("edge:07", selection.allowed_edge_ids)

    def test_missing_impact_result_is_insufficient(self) -> None:
        request = dependency_request().model_copy(update={"query": "What breaks if api.py fails?", "impactAnalysis": None})
        selection = EvidenceSelector().select(request, QueryRouter().route(request.query))
        self.assertFalse(selection.sufficient)
        self.assertIn("BFS", selection.missing_description or "")


if __name__ == "__main__":
    unittest.main()
