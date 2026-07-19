import unittest
from unittest.mock import patch

from app.core.config import GeminiSettings
from app.core.exceptions import ExplanationConfigurationError
from app.services.explanations.gemini_client import GeminiClient


class RecordingTransport:
    def __init__(self) -> None:
        self.model = ""
        self.prompt = ""

    def generate(self, *, model: str, prompt: str) -> str:
        self.model = model
        self.prompt = prompt
        return "{}"


class GeminiClientTest(unittest.TestCase):
    def test_uses_configured_model_without_exposing_key(self) -> None:
        transport = RecordingTransport()
        client = GeminiClient(GeminiSettings(apiKey="secret-value"), transport)

        self.assertEqual("{}", client.generate("grounded prompt"))
        self.assertEqual("gemini-3.1-flash-lite", transport.model)
        self.assertNotIn("secret-value", transport.prompt)

    def test_missing_key_fails_before_provider_call(self) -> None:
        with patch.dict("os.environ", {}, clear=True):
            with self.assertRaises(ExplanationConfigurationError):
                GeminiSettings.from_environment()
            with self.assertRaises(ExplanationConfigurationError):
                GeminiClient().generate("prompt")


if __name__ == "__main__":
    unittest.main()
