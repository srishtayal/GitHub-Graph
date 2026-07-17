"""Resolve Phase 6 failure evidence against an immutable repository graph."""

from __future__ import annotations

import re
from pathlib import PurePosixPath

from app.analytics.graph_projection import GraphProjection
from app.schemas.failure_analysis import ErrorSignature, FailureInput, ResolvedFailurePath, UnresolvedReference
from app.schemas.responses import GraphPayload

_STACK_FRAME = re.compile(r'File "(?P<path>[^"]+)", line (?P<line>\d+)(?:, in (?P<name>[^\n]+))?')
_ERROR_LINE = re.compile(r"(?m)^\s*(?P<type>[A-Za-z_][\w.]*(?:Error|Exception|Warning|Exit))\s*:\s*(?P<message>.+?)\s*$")


class FailurePathParser:
    def parse(self, graph: GraphPayload | GraphProjection, failure: FailureInput) -> ResolvedFailurePath:
        projection = graph if isinstance(graph, GraphProjection) else GraphProjection.from_payload(graph)
        node_ids: list[str] = []
        stack_node_ids: list[str] = []
        unresolved: list[UnresolvedReference] = []

        for node_id in failure.failurePathNodeIds:
            if projection.contains_node(node_id):
                _append_once(node_ids, node_id)
            else:
                unresolved.append(UnresolvedReference(kind="node_id", value=node_id, detail="Unknown graph node ID"))
        if failure.failingNodeId:
            if projection.contains_node(failure.failingNodeId):
                _append_once(node_ids, failure.failingNodeId)
            else:
                unresolved.append(
                    UnresolvedReference(
                        kind="failing_node_id",
                        value=failure.failingNodeId,
                        detail="Unknown graph node ID",
                    )
                )

        evidence_text = "\n".join(part for part in (failure.stackTrace, failure.errorLog) if part)
        for match in _STACK_FRAME.finditer(evidence_text):
            frame_value = match.group(0)
            resolved = _resolve_stack_frame(
                projection,
                match.group("path"),
                int(match.group("line")),
                match.group("name"),
            )
            if resolved is None:
                unresolved.append(
                    UnresolvedReference(kind="stack_frame", value=frame_value, detail="No unique graph function match")
                )
                continue
            _append_once(node_ids, resolved)
            _append_once(stack_node_ids, resolved)

        return ResolvedFailurePath(
            nodeIds=node_ids,
            stackFrameNodeIds=stack_node_ids,
            errorSignature=_error_signature(evidence_text),
            unresolvedReferences=unresolved,
        )


def _resolve_stack_frame(
    projection: GraphProjection,
    frame_path: str,
    line_number: int,
    frame_name: str | None,
) -> str | None:
    matching_files = _matching_relative_paths(projection, frame_path)
    candidates = [
        node
        for node in projection.nodes_by_id.values()
        if node.type == "function" and node.properties.get("relativePath") in matching_files
    ]
    in_range = [
        node
        for node in candidates
        if isinstance(node.properties.get("startLine"), int)
        and isinstance(node.properties.get("endLine"), int)
        and node.properties["startLine"] <= line_number <= node.properties["endLine"]
    ]
    if frame_name:
        name_matches = [
            node for node in in_range or candidates
            if frame_name in {node.label, node.properties.get("name"), node.properties.get("qualifiedName")}
        ]
        if len(name_matches) == 1:
            return name_matches[0].id
    if len(in_range) == 1:
        return in_range[0].id
    return None


def _matching_relative_paths(projection: GraphProjection, frame_path: str) -> set[str]:
    normalized_frame = PurePosixPath(frame_path).as_posix()
    matches: set[str] = set()
    for node in projection.nodes_by_id.values():
        path = node.properties.get("relativePath")
        if not isinstance(path, str):
            continue
        normalized_path = PurePosixPath(path).as_posix()
        if normalized_frame == normalized_path or normalized_frame.endswith(f"/{normalized_path}"):
            matches.add(path)
    return matches


def _error_signature(text: str) -> ErrorSignature:
    matches = list(_ERROR_LINE.finditer(text))
    if not matches:
        return ErrorSignature()
    match = matches[-1]
    return ErrorSignature(
        exceptionType=match.group("type").rsplit(".", 1)[-1],
        messageFingerprint=_fingerprint(match.group("message")),
    )


def _fingerprint(message: str) -> str:
    return "-".join(re.findall(r"[a-z0-9]+", message.lower()))


def _append_once(values: list[str], value: str) -> None:
    if value not in values:
        values.append(value)
