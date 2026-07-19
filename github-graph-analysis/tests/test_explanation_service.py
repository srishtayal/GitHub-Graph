import unittest

from app.core.config import GeminiSettings
from app.services.explanations.explanation_service import ExplanationService
from app.services.explanations.gemini_client import GeminiClient
from tests.fixtures.phase7_explanations import dependency_request, grounded_dependency_response


class FakeTransport:
    def __init__(self, response: str) -> None:
        self.response = response
        self.call_count = 0

    def generate(self, *, model: str, prompt: str) -> str:
        self.call_count += 1
        return self.response


class ExplanationServiceTest(unittest.TestCase):
    def test_returns_grounded_model_response(self) -> None:
        transport = FakeTransport(grounded_dependency_response())
        service = ExplanationService(
            gemini_client=GeminiClient(GeminiSettings(apiKey="test"), transport)
        )

        response = service.explain(dependency_request())
        self.assertEqual("dependency_flow", response.intent)
        self.assertEqual("high", response.confidence)
        self.assertEqual(1, transport.call_count)

    def test_missing_required_analysis_returns_locally_without_model_call(self) -> None:
        transport = FakeTransport(grounded_dependency_response())
        service = ExplanationService(
            gemini_client=GeminiClient(GeminiSettings(apiKey="test"), transport)
        )
        request = dependency_request().model_copy(update={"query": "What breaks if api.py fails?"})

        response = service.explain(request)
        self.assertEqual("insufficient", response.confidence)
        self.assertEqual(0, transport.call_count)


if __name__ == "__main__":
    unittest.main()
