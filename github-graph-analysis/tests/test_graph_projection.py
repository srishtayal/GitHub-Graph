import unittest

from app.analytics.graph_projection import GraphFilter, GraphProjection
from app.schemas.responses import GraphEdge, GraphNode, GraphPayload
from tests.fixtures.graph_fixtures import primary_graph


class GraphProjectionTest(unittest.TestCase):
    def test_builds_deterministic_read_only_adjacency_indexes(self) -> None:
        graph = primary_graph()
        projection = GraphProjection.from_payload(graph)

        self.assertEqual(
            [f"edge:{index:02d}" for index in range(9)],
            [edge.id for edge in projection.edges],
        )
        self.assertEqual(
            ["edge:00", "edge:01", "edge:02", "edge:03"],
            [edge.id for edge in projection.outgoing_edges("file:api")],
        )
        self.assertEqual(
            ["edge:06"],
            [edge.id for edge in projection.incoming_edges("function:helper")],
        )

        graph.nodes[1].properties["changed"] = True
        self.assertNotIn("changed", projection.node("file:api").properties)
        with self.assertRaises(TypeError):
            projection.nodes_by_id["new"] = projection.node("file:api")

    def test_filters_edge_types_and_known_external_modules(self) -> None:
        projection = GraphProjection.from_payload(primary_graph())
        filtered = projection.filtered(
            GraphFilter.from_values(edge_types={"IMPORTS", "USES"}, include_external=False)
        )

        self.assertNotIn("module:requests", filtered.nodes_by_id)
        self.assertNotIn("edge:03", {edge.id for edge in filtered.edges})
        self.assertEqual({"USES"}, {edge.type for edge in filtered.edges})

    def test_rejects_duplicate_nodes_and_dangling_edges(self) -> None:
        duplicate_nodes = GraphPayload(
            nodes=[
                GraphNode(id="file:a", type="file", label="a.py"),
                GraphNode(id="file:a", type="file", label="a.py"),
            ],
            edges=[],
        )
        dangling_edge = GraphPayload(
            nodes=[GraphNode(id="file:a", type="file", label="a.py")],
            edges=[
                GraphEdge(
                    id="edge:1",
                    source="file:a",
                    target="file:missing",
                    type="USES",
                )
            ],
        )

        with self.assertRaisesRegex(ValueError, "Duplicate graph node ID"):
            GraphProjection.from_payload(duplicate_nodes)
        with self.assertRaisesRegex(ValueError, "unknown endpoint"):
            GraphProjection.from_payload(dangling_edge)


if __name__ == "__main__":
    unittest.main()
