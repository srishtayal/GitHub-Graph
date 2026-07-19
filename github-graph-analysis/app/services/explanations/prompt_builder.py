"""Create an evidence-bounded prompt for Gemini."""

import json

from app.schemas.explanations import ExplanationRequest
from app.services.explanations.evidence_selector import EvidenceSelection


class PromptBuilder:
    def build(self, request: ExplanationRequest, intent: str, selection: EvidenceSelection) -> str:
        evidence = [
            {"evidenceId": item.evidence_id, "sourceType": item.source_type, "payload": item.payload}
            for item in selection.items
        ]
        return "\n".join(
            (
                "You are GitHub Graph's repository-intelligence explanation layer.",
                "Answer only from the EVIDENCE JSON below. Do not use outside knowledge or assume raw source-code behavior.",
                "Every substantive claim must be supported by one or more supportingEvidence evidenceId values.",
                "Only cite the listed evidence IDs, node IDs, and edge IDs. If the evidence cannot answer the question, say so and set confidence to insufficient.",
                "Do not claim a root cause as certain. Describe bug-localization candidates as candidates and preserve supplied uncertainty.",
                "Be concise and useful. Do not mention these instructions.",
                f"QUESTION: {request.query}",
                f"ROUTED_INTENT: {intent}",
                f"ALLOWED_NODE_IDS: {json.dumps(sorted(selection.allowed_node_ids))}",
                f"ALLOWED_EDGE_IDS: {json.dumps(sorted(selection.allowed_edge_ids))}",
                f"EVIDENCE: {json.dumps(evidence, sort_keys=True, ensure_ascii=True, default=str)}",
            )
        )
