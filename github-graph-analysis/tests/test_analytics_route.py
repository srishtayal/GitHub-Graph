import unittest

from fastapi.testclient import TestClient

from app.main import app
from app.schemas.responses import GraphEdge, GraphNode, GraphPayload


class AnalyticsRouteTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)
        self.graph = GraphPayload(
            nodes=[
                GraphNode(id="file:a", type="file", label="a.py"),
                GraphNode(id="file:b", type="file", label="b.py"),
                GraphNode(id="fn:main", type="function", label="a.main"),
                GraphNode(id="fn:helper", type="function", label="b.helper"),
            ],
            edges=[
                GraphEdge(id="e1", source="file:a", target="file:b", type="USES"),
                GraphEdge(id="e2", source="fn:main", target="fn:helper", type="CALLS"),
                GraphEdge(id="e3", source="fn:main", target="file:a", type="BELONGS_TO"),
                GraphEdge(id="e4", source="fn:helper", target="file:b", type="BELONGS_TO"),
            ],
        )

    def test_graph_analytics_endpoint_returns_insights(self) -> None:
        response = self.client.post(
            "/internal/v1/graph-analytics",
            json={"graph": self.graph.model_dump(), "maxDepth": 5},
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertIn("insights", body)
        self.assertIn("connected_components", body)
        self.assertIn("topological_sort", body)
        self.assertIn("centrality", body)
        self.assertIn("cycles", body)
        self.assertIsNone(body["node_analysis"])

    def test_graph_analytics_with_node_returns_trace_and_impact(self) -> None:
        response = self.client.post(
            "/internal/v1/graph-analytics",
            json={
                "graph": self.graph.model_dump(),
                "nodeId": "a.py",
                "maxDepth": 5,
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertIsNotNone(body["node_analysis"])
        self.assertEqual("file:a", body["node_analysis"]["node_id"])
        self.assertIn("dependency_trace", body["node_analysis"])
        self.assertIn("impact_spread", body["node_analysis"])
        self.assertIn("selected_node", body["insights"])

    def test_graph_analytics_unknown_node_returns_404(self) -> None:
        response = self.client.post(
            "/internal/v1/graph-analytics",
            json={
                "graph": self.graph.model_dump(),
                "nodeId": "missing.py",
            },
        )

        self.assertEqual(404, response.status_code)


if __name__ == "__main__":
    unittest.main()
