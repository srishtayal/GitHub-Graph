import tempfile
import unittest
from pathlib import Path

from app.schemas.responses import FileMetadata
from app.services.graph_planner import GraphBuilder, build_graph_payload
from app.services.static_code_extractor import extract_static_code


class GraphPlannerTest(unittest.TestCase):
    def test_builds_all_node_and_edge_types_with_resolved_relationships(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "models.py").write_text(
                "class User:\n    @classmethod\n    def save(cls, value):\n        return value\n",
                encoding="utf-8",
            )
            (root / "api.py").write_text(
                """from .models import User
import jwt

router = APIRouter(prefix="/v1")

class Admin(User):
    def helper(self):
        return True

    @router.post("/users")
    def create_user(self, payload):
        self.helper()
        token = jwt.encode(payload)
        return User.save(token)
""",
                encoding="utf-8",
            )
            (root / "README.md").write_text("# Example", encoding="utf-8")
            files = [
                _file("api.py", "Python"),
                _file("models.py", "Python"),
                _file("README.md", "Markdown"),
            ]
            parsed = extract_static_code(root, files)

        graph = build_graph_payload(
            repository_id="repo-123",
            github_url="https://github.com/example/project.git",
            files=files,
            parsed=parsed,
        )
        nodes = {node.id: node for node in graph.nodes}
        nodes_by_label = {node.label: node for node in graph.nodes}
        edges = graph.edges

        self.assertEqual(
            {"repo", "file", "class", "function", "api", "module"},
            {node.type for node in graph.nodes},
        )
        self.assertEqual(
            {"BELONGS_TO", "IMPORTS", "CALLS", "USES", "INHERITS"},
            {edge.type for edge in edges},
        )
        self.assertEqual("repo", nodes_by_label["project"].type)
        self.assertEqual("file", nodes_by_label["README.md"].type)
        self.assertEqual("api", nodes_by_label["POST /v1/users"].type)

        self.assertTrue(_has_edge(edges, nodes_by_label["api.py"].id, nodes_by_label["project"].id, "BELONGS_TO"))
        self.assertTrue(_has_edge(edges, nodes_by_label["Admin"].id, nodes_by_label["api.py"].id, "BELONGS_TO"))
        self.assertTrue(_has_edge(edges, nodes_by_label["Admin.create_user"].id, nodes_by_label["Admin"].id, "BELONGS_TO"))
        self.assertTrue(_has_edge(edges, nodes_by_label["api.py"].id, nodes_by_label["jwt"].id, "IMPORTS"))
        self.assertTrue(_has_edge(edges, nodes_by_label["api.py"].id, nodes_by_label["models.py"].id, "USES"))
        self.assertTrue(_has_edge(edges, nodes_by_label["Admin"].id, nodes_by_label["User"].id, "INHERITS"))
        self.assertTrue(_has_edge(edges, nodes_by_label["POST /v1/users"].id, nodes_by_label["Admin.create_user"].id, "USES"))
        self.assertTrue(_has_edge(edges, nodes_by_label["Admin.create_user"].id, nodes_by_label["Admin.helper"].id, "CALLS"))
        self.assertTrue(_has_edge(edges, nodes_by_label["Admin.create_user"].id, nodes_by_label["jwt"].id, "CALLS"))
        self.assertTrue(_has_edge(edges, nodes_by_label["Admin.create_user"].id, nodes_by_label["User.save"].id, "CALLS"))

        self.assertEqual(len(nodes), len(graph.nodes))
        self.assertEqual(len({edge.id for edge in edges}), len(edges))

    def test_output_is_stable_and_duplicate_edges_are_collapsed(self) -> None:
        builder = GraphBuilder("repo-123")
        source = builder.add_node(builder.node_id("file", "app.py"), "file", "app.py")
        target = builder.add_node(builder.node_id("repo"), "repo", "project")
        builder.add_edge(source, target, "BELONGS_TO")
        builder.add_edge(source, target, "BELONGS_TO")

        self.assertEqual(1, len(builder.payload().edges))

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "app.py").write_text("def run():\n    print('ok')\n", encoding="utf-8")
            files = [_file("app.py", "Python")]
            parsed = extract_static_code(root, files)

        first = build_graph_payload("repo-123", "https://github.com/example/project", files, parsed)
        second = build_graph_payload("repo-123", "https://github.com/example/project", files, parsed)
        self.assertEqual(first.model_dump(), second.model_dump())


def _has_edge(edges, source: str, target: str, edge_type: str) -> bool:
    return any(
        edge.source == source and edge.target == target and edge.type == edge_type
        for edge in edges
    )


def _file(relative_path: str, language: str) -> FileMetadata:
    path = Path(relative_path)
    return FileMetadata(
        relativePath=relative_path,
        fileName=path.name,
        extension=path.suffix,
        language=language,
        sizeBytes=0,
        isBinary=False,
    )


if __name__ == "__main__":
    unittest.main()
