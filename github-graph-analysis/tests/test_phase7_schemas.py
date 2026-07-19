import unittest

from app.schemas.explanations import ExplanationRequest, ExplanationResponse
from tests.fixtures.graph_fixtures import primary_graph


class Phase7SchemaTest(unittest.TestCase):
    def test_request_accepts_precomputed_evidence_without_raw_repository_path(self) -> None:
        request = ExplanationRequest(
            query="Explain the repository structure",
            repositoryId="repo-123",
            graph=primary_graph(),
        )

        self.assertEqual("repo-123", request.repositoryId)
        self.assertFalse(hasattr(request, "localPath"))

    def test_response_requires_the_grounded_contract(self) -> None:
        response = ExplanationResponse(
            intent="criticality",
            answer="process has the highest supplied score.",
            confidence="medium",
        )
        self.assertEqual("medium", response.confidence)


if __name__ == "__main__":
    unittest.main()
