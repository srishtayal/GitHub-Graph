"""Resolve a graph target explicitly or from a natural-language question."""

import re

from app.schemas.responses import GraphNode, GraphPayload


class TargetResolver:
    def resolve(
        self,
        graph: GraphPayload,
        query: str,
        explicit_node_id: str | None,
    ) -> tuple[GraphNode | None, str | None]:
        nodes_by_id = {node.id: node for node in graph.nodes}
        if explicit_node_id:
            node = nodes_by_id.get(explicit_node_id)
            if node is None:
                return None, f"Target node does not exist in this snapshot: {explicit_node_id}"
            return node, None

        query_normalized = _normalize(query)
        query_tokens = set(_tokens(query))
        ranked: list[tuple[float, str, GraphNode]] = []
        for node in graph.nodes:
            if node.type == "repo":
                continue
            names = _candidate_names(node)
            score = max((_score_name(name, query_normalized, query_tokens) for name in names), default=0.0)
            if score > 0:
                ranked.append((score, node.id, node))

        if not ranked:
            return None, "No graph node could be resolved from the question."
        ranked.sort(key=lambda item: (-item[0], item[1]))
        if len(ranked) > 1 and ranked[0][0] == ranked[1][0]:
            labels = ", ".join(item[2].label for item in ranked[:3])
            return None, f"The target is ambiguous between: {labels}."
        return ranked[0][2], None


def _candidate_names(node: GraphNode) -> set[str]:
    values = {node.label}
    for key in ("name", "qualifiedName", "relativePath", "path", "handler"):
        value = node.properties.get(key)
        if isinstance(value, str) and value:
            values.add(value)
            values.add(value.rsplit("/", 1)[-1])
            values.add(value.rsplit(".", 1)[-1])
    return values


def _score_name(name: str, query_normalized: str, query_tokens: set[str]) -> float:
    normalized = _normalize(name)
    if not normalized:
        return 0.0
    if normalized in query_normalized:
        return 100.0 + len(normalized)
    name_tokens = set(_tokens(name))
    overlap = name_tokens & query_tokens
    if not overlap:
        return 0.0
    return len(overlap) / len(name_tokens)


def _normalize(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", value.casefold())


def _tokens(value: str) -> list[str]:
    expanded = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", value)
    return [
        token.casefold()
        for token in re.findall(r"[A-Za-z0-9_]+", expanded)
        if len(token) >= 3
    ]
