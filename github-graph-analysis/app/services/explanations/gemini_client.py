"""Small, injectable adapter around the official Google GenAI Python SDK."""

import time
from typing import Protocol

from app.core.config import GeminiSettings
from app.core.exceptions import ExplanationProviderError
from app.schemas.explanations import ExplanationResponse


class GeminiTransport(Protocol):
    def generate(self, *, model: str, prompt: str) -> str: ...


class GeminiClient:
    def __init__(
        self,
        settings: GeminiSettings | None = None,
        transport: GeminiTransport | None = None,
        sleeper=time.sleep,
    ) -> None:
        self._settings = settings
        self._transport = transport
        self._sleeper = sleeper

    def generate(self, prompt: str) -> str:
        settings = self.settings
        last_error: Exception | None = None
        for attempt in range(settings.maxRetries + 1):
            try:
                result = self._generate_once(settings, prompt)
                if not result:
                    raise ExplanationProviderError("Gemini returned an empty explanation response")
                return result
            except ExplanationProviderError as error:
                last_error = error
            except Exception as error:
                last_error = error
            if attempt < settings.maxRetries:
                self._sleeper(settings.retryBackoffSeconds * (2**attempt))
        raise ExplanationProviderError("Gemini explanation request failed") from last_error

    def _generate_once(self, settings: GeminiSettings, prompt: str) -> str:
        if self._transport:
            return self._transport.generate(model=settings.model, prompt=prompt)
        try:
            from google import genai
            from google.genai import types

            client = genai.Client(
                api_key=settings.apiKey,
                http_options=types.HttpOptions(timeout=int(settings.timeoutSeconds * 1000)),
            )
            response = client.models.generate_content(
                model=settings.model,
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    response_schema=ExplanationResponse,
                    temperature=0.0,
                ),
            )
            return response.text or ""
        except Exception as error:
            raise ExplanationProviderError("Gemini explanation request failed") from error

    @property
    def settings(self) -> GeminiSettings:
        if self._settings is None:
            self._settings = GeminiSettings.from_environment()
        return self._settings
