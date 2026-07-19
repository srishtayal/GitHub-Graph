"""Internal HTTP access to the grounded Phase 7 explanation service."""

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.core.exceptions import (
    ExplanationConfigurationError,
    ExplanationProviderError,
    ExplanationResponseError,
)
from app.schemas.explanations import ExplanationRequest, ExplanationResponse
from app.services.explanations.explanation_service import ExplanationService

router = APIRouter(prefix="/internal/v1")


def get_explanation_service() -> ExplanationService:
    """Construct the service lazily so evidence-only requests need no API key."""
    return ExplanationService()


@router.post("/explanations", response_model=ExplanationResponse)
def create_explanation(
    request: ExplanationRequest,
    service: Annotated[ExplanationService, Depends(get_explanation_service)],
) -> ExplanationResponse:
    try:
        return service.explain(request)
    except ExplanationConfigurationError as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Gemini explanation service is not configured",
        ) from error
    except (ExplanationProviderError, ExplanationResponseError) as error:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Gemini explanation service could not produce a valid response",
        ) from error
