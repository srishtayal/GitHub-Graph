"""Small, injectable adapter around the official Google GenAI Python SDK."""

from typing import Protocol

from app.core.config import GeminiSettings
from app.core.exceptions import ExplanationProviderError
from app.schemas.explanations import ExplanationResponse


class GeminiTransport(Protocol):
    def generate(self, *, model: str, prompt: str) -> str: ...


class GeminiClient:
    def __init__(self, settings: GeminiSettings | None = None, transport: GeminiTransport | None = None) -> None:
        self._settings = settings
        self._transport = transport

    def generate(self, prompt: str) -> str:
        settings = self.settings
        if self._transport:
            return self._transport.generate(model=settings.model, prompt=prompt)
        try:
            from google import genai
            from google.genai import types

            client = genai.Client(api_key=settings.apiKey)
            response = client.models.generate_content(
                model=settings.model,
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    response_schema=ExplanationResponse,
                    temperature=0.0,
                ),
            )
            if not response.text:
                raise ExplanationProviderError("Gemini returned an empty explanation response")
            return response.text
        except ExplanationProviderError:
            raise
        except Exception as error:
            raise ExplanationProviderError("Gemini explanation request failed") from error

    @property
    def settings(self) -> GeminiSettings:
        if self._settings is None:
            self._settings = GeminiSettings.from_environment()
        return self._settings
