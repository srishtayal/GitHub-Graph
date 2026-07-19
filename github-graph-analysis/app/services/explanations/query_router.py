"""Deterministic mapping from a user question to existing analysis evidence."""

from app.schemas.explanations import ExplanationIntent


class QueryRouter:
    """Use transparent keyword routing; this is intentionally not an LLM decision."""

    def route(self, query: str) -> ExplanationIntent:
        normalized = query.casefold()
        if any(term in normalized for term in ("similar", "similarity", "alike", "compare these")):
            return "similarity"
        if any(term in normalized for term in ("break", "impact", "downstream", "what happens if", "fails")):
            return "impact_analysis"
        if any(term in normalized for term in ("root cause", "why is this error", "why this error", "failure", "exception", "bug")):
            return "bug_explanation"
        if any(term in normalized for term in ("critical", "important", "central", "most used")):
            return "criticality"
        if any(term in normalized for term in ("cycle", "topological", "build order", "dependency order")):
            return "cycle_or_order"
        if any(term in normalized for term in ("flow", "dependency", "dependencies", "trace", "path")):
            return "dependency_flow"
        if any(term in normalized for term in ("structure", "layout", "repository", "repo", "modules")):
            return "repository_structure"
        return "unknown_or_insufficient"
