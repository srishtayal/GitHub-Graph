"""Validate model output against both its Pydantic schema and supplied evidence."""

from app.core.exceptions import ExplanationResponseError
from app.schemas.explanations import ExplanationResponse
from app.services.explanations.evidence_selector import EvidenceSelection


class ResponseParser:
    def parse(self, raw_response: str, selection: EvidenceSelection) -> ExplanationResponse:
        try:
            response = ExplanationResponse.model_validate_json(raw_response)
        except Exception as error:
            raise ExplanationResponseError("Gemini returned an invalid explanation response") from error

        valid_evidence = {item.evidence_id: item.source_type for item in selection.items}
        invalid_evidence = [
            reference.evidenceId
            for reference in response.supportingEvidence
            if (
                reference.evidenceId not in valid_evidence
                or reference.sourceType != valid_evidence[reference.evidenceId]
            )
        ]
        invalid_nodes = [
            node_id for node_id in response.referencedNodeIds if node_id not in selection.allowed_node_ids
        ]
        invalid_edges = [
            edge_id for edge_id in response.referencedEdgeIds if edge_id not in selection.allowed_edge_ids
        ]
        if invalid_evidence or invalid_nodes or invalid_edges:
            raise ExplanationResponseError("Gemini cited evidence or graph references that were not supplied")

        if not response.supportingEvidence:
            raise ExplanationResponseError("Gemini cited no evidence that was supplied to it")

        if response.confidence == "high" and not selection.sufficient:
            response.confidence = "low"
        return response
