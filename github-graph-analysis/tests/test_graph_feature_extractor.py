import unittest

from app.services.graph_feature_extractor import GraphFeatureExtractor
from tests.fixtures.phase6_graphs import similarity_graph


class GraphFeatureExtractorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.index = GraphFeatureExtractor().build_index(similarity_graph())

    def test_extracts_function_features_from_calls_neighbors_and_enclosing_file(self) -> None:
        features = self.index.features_for("function:login-a")

        self.assertEqual("function", features.nodeType)
        self.assertEqual({"call:function:validate", "call:module:jwt"}, features.features["calledNodes"])
        self.assertEqual({"import:module:jwt"}, features.features["enclosingFileImports"])
        self.assertIn("neighbor:file:auth-a", features.features["neighborNodes"])

    def test_extracts_file_features(self) -> None:
        features = self.index.features_for("file:auth-a")

        self.assertEqual({"import:module:jwt"}, features.features["importedModules"])
        self.assertEqual(
            {"symbol:function:login-a", "symbol:function:validate"},
            features.features["containedSymbols"],
        )

    def test_extracts_module_features(self) -> None:
        features = self.index.features_for("module:jwt")

        self.assertEqual(
            {"importer:file:auth-a", "importer:file:auth-b"},
            features.features["importingFiles"],
        )
        self.assertEqual(
            {"user:file:auth-a", "user:file:auth-b"},
            features.features["usingFiles"],
        )

    def test_rejects_unknown_or_unsupported_nodes(self) -> None:
        with self.assertRaisesRegex(ValueError, "Unknown graph node ID"):
            self.index.features_for("function:missing")
        with self.assertRaisesRegex(ValueError, "Unsupported similarity node type"):
            self.index.features_for("repo:sample")


if __name__ == "__main__":
    unittest.main()
