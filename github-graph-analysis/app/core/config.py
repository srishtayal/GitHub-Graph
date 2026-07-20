import os

from pydantic import BaseModel, Field


class Settings(BaseModel):
    host: str = "0.0.0.0"
    port: int = 8000


settings = Settings()


class GeminiSettings(BaseModel):
    """Runtime configuration for the Phase 7 Gemini client."""

    apiKey: str
    model: str = "gemini-3.1-flash-lite"
    timeoutSeconds: float = Field(default=30.0, gt=0)
    maxRetries: int = Field(default=2, ge=0, le=5)
    retryBackoffSeconds: float = Field(default=0.5, ge=0, le=10)

    @classmethod
    def from_environment(cls) -> "GeminiSettings":
        api_key = os.environ.get("GEMINI_API_KEY")
        if not api_key:
            from app.core.exceptions import ExplanationConfigurationError

            raise ExplanationConfigurationError("GEMINI_API_KEY must be configured")
        return cls(
            apiKey=api_key,
            model=os.environ.get("GEMINI_MODEL", "gemini-3.1-flash-lite"),
            timeoutSeconds=float(os.environ.get("GEMINI_TIMEOUT_SECONDS", "30")),
            maxRetries=int(os.environ.get("GEMINI_MAX_RETRIES", "2")),
            retryBackoffSeconds=float(os.environ.get("GEMINI_RETRY_BACKOFF_SECONDS", "0.5")),
        )


class ExplanationLimits(BaseModel):
    """Bounds applied before any repository evidence reaches a provider."""

    maxPromptChars: int = Field(default=30000, ge=2000, le=200000)
    maxEvidenceChars: int = Field(default=20000, ge=1000, le=150000)
    maxEvidenceItems: int = Field(default=12, ge=1, le=50)
    maxReferencedNodes: int = Field(default=80, ge=1, le=1000)
    maxReferencedEdges: int = Field(default=120, ge=0, le=2000)

    @classmethod
    def from_environment(cls) -> "ExplanationLimits":
        return cls(
            maxPromptChars=int(os.environ.get("EXPLANATION_MAX_PROMPT_CHARS", "30000")),
            maxEvidenceChars=int(os.environ.get("EXPLANATION_MAX_EVIDENCE_CHARS", "20000")),
            maxEvidenceItems=int(os.environ.get("EXPLANATION_MAX_EVIDENCE_ITEMS", "12")),
            maxReferencedNodes=int(os.environ.get("EXPLANATION_MAX_REFERENCED_NODES", "80")),
            maxReferencedEdges=int(os.environ.get("EXPLANATION_MAX_REFERENCED_EDGES", "120")),
        )
