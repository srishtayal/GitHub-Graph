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

        valid_evidence_ids = {item.evidence_id for item in selection.items}
        valid_evidence = [reference for reference in response.supportingEvidence if reference.evidenceId in valid_evidence_ids]
        valid_nodes = [node_id for node_id in response.referencedNodeIds if node_id in selection.allowed_node_ids]
        valid_edges = [edge_id for edge_id in response.referencedEdgeIds if edge_id in selection.allowed_edge_ids]

        if response.supportingEvidence and not valid_evidence:
            raise ExplanationResponseError("Gemini cited no evidence that was supplied to it")

        response.supportingEvidence = valid_evidence
        response.referencedNodeIds = valid_nodes
        response.referencedEdgeIds = valid_edges
        if not valid_evidence:
            response.confidence = "insufficient"
            response.limitations = [*response.limitations, "The response did not cite supplied graph evidence."]
        elif response.confidence == "high" and not selection.sufficient:
            response.confidence = "low"
        return response
