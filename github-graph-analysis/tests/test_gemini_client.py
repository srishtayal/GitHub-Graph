import unittest
from unittest.mock import patch

from app.core.config import GeminiSettings
from app.core.exceptions import ExplanationConfigurationError, ExplanationProviderError
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

    def test_retries_provider_failures_with_bounded_backoff(self) -> None:
        class FlakyTransport:
            def __init__(self) -> None:
                self.calls = 0

            def generate(self, *, model: str, prompt: str) -> str:
                self.calls += 1
                if self.calls < 3:
                    raise TimeoutError("provider timeout")
                return "{}"

        transport = FlakyTransport()
        sleeps = []
        client = GeminiClient(
            GeminiSettings(
                apiKey="test",
                maxRetries=2,
                retryBackoffSeconds=0.25,
                timeoutSeconds=1,
            ),
            transport,
            sleeper=sleeps.append,
        )

        self.assertEqual("{}", client.generate("prompt"))
        self.assertEqual(3, transport.calls)
        self.assertEqual([0.25, 0.5], sleeps)

    def test_stops_after_configured_retry_limit(self) -> None:
        class FailedTransport:
            def generate(self, *, model: str, prompt: str) -> str:
                raise TimeoutError("provider timeout")

        with self.assertRaises(ExplanationProviderError):
            GeminiClient(
                GeminiSettings(apiKey="test", maxRetries=1, retryBackoffSeconds=0),
                FailedTransport(),
                sleeper=lambda _: None,
            ).generate("prompt")


if __name__ == "__main__":
    unittest.main()
