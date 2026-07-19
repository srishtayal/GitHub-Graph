"""Orchestrate grounded explanation generation without performing raw-code analysis."""

from app.schemas.explanations import ExplanationRequest, ExplanationResponse
from app.services.explanations.evidence_selector import EvidenceSelector
from app.services.explanations.gemini_client import GeminiClient
from app.services.explanations.prompt_builder import PromptBuilder
from app.services.explanations.query_router import QueryRouter
from app.services.explanations.response_parser import ResponseParser


class ExplanationService:
    def __init__(
        self,
        router: QueryRouter | None = None,
        selector: EvidenceSelector | None = None,
        prompt_builder: PromptBuilder | None = None,
        gemini_client: GeminiClient | None = None,
        response_parser: ResponseParser | None = None,
    ) -> None:
        self._router = router or QueryRouter()
        self._selector = selector or EvidenceSelector()
        self._prompt_builder = prompt_builder or PromptBuilder()
        self._gemini_client = gemini_client or GeminiClient()
        self._response_parser = response_parser or ResponseParser()

    def explain(self, request: ExplanationRequest) -> ExplanationResponse:
        intent = self._router.route(request.query)
        selection = self._selector.select(request, intent)
        if not selection.sufficient:
            return ExplanationResponse(
                intent=intent,
                answer="I cannot answer this from the supplied graph-analysis evidence.",
                confidence="insufficient",
                limitations=[selection.missing_description or "Evidence is insufficient."],
                followUpSuggestions=["Provide the required structured analysis result, then ask again."],
            )
        prompt = self._prompt_builder.build(request, intent, selection)
        response = self._response_parser.parse(self._gemini_client.generate(prompt), selection)
        response.intent = intent
        return response
