class AnalysisError(Exception):
    """Base exception for analysis failures."""


class ExplanationError(AnalysisError):
    """Base exception for Phase 7 explanation failures."""


class ExplanationConfigurationError(ExplanationError):
    """Raised when Gemini configuration is unavailable or invalid."""


class ExplanationProviderError(ExplanationError):
    """Raised when Gemini cannot produce a usable response."""


class ExplanationResponseError(ExplanationError):
    """Raised when a model response does not satisfy the explanation contract."""
