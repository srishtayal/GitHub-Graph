import unittest

from fastapi.testclient import TestClient

from app.core.exceptions import ExplanationConfigurationError, ExplanationProviderError
from app.main import app
from app.api.routes.explanations import get_explanation_service
from tests.fixtures.phase7_explanations import dependency_request, grounded_dependency_response


class FakeExplanationService:
    def __init__(self, response: str | Exception) -> None:
        self._response = response

    def explain(self, request):
        if isinstance(self._response, Exception):
            raise self._response
        from app.schemas.explanations import ExplanationResponse

        return ExplanationResponse.model_validate_json(self._response)


class ExplanationsRouteTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = TestClient(app)

    def tearDown(self) -> None:
        app.dependency_overrides.clear()

    def test_returns_structured_explanation(self) -> None:
        app.dependency_overrides[get_explanation_service] = lambda: FakeExplanationService(
            grounded_dependency_response()
        )

        response = self.client.post("/internal/v1/explanations", json=dependency_request().model_dump(mode="json"))

        self.assertEqual(200, response.status_code)
        self.assertEqual("dependency_flow", response.json()["intent"])
        self.assertEqual("high", response.json()["confidence"])

    def test_returns_503_when_gemini_is_not_configured(self) -> None:
        app.dependency_overrides[get_explanation_service] = lambda: FakeExplanationService(
            ExplanationConfigurationError("missing key")
        )

        response = self.client.post("/internal/v1/explanations", json=dependency_request().model_dump(mode="json"))

        self.assertEqual(503, response.status_code)
        self.assertEqual("Gemini explanation service is not configured", response.json()["detail"])

    def test_returns_502_when_gemini_provider_fails(self) -> None:
        app.dependency_overrides[get_explanation_service] = lambda: FakeExplanationService(
            ExplanationProviderError("provider failure")
        )

        response = self.client.post("/internal/v1/explanations", json=dependency_request().model_dump(mode="json"))

        self.assertEqual(502, response.status_code)
        self.assertEqual("Gemini explanation service could not produce a valid response", response.json()["detail"])


if __name__ == "__main__":
    unittest.main()
