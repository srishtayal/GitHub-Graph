import os

from pydantic import BaseModel


class Settings(BaseModel):
    host: str = "0.0.0.0"
    port: int = 8000


settings = Settings()


class GeminiSettings(BaseModel):
    """Runtime configuration for the Phase 7 Gemini client."""

    apiKey: str
    model: str = "gemini-3.1-flash-lite"

    @classmethod
    def from_environment(cls) -> "GeminiSettings":
        api_key = os.environ.get("GEMINI_API_KEY")
        if not api_key:
            from app.core.exceptions import ExplanationConfigurationError

            raise ExplanationConfigurationError("GEMINI_API_KEY must be configured")
        return cls(apiKey=api_key, model=os.environ.get("GEMINI_MODEL", "gemini-3.1-flash-lite"))
