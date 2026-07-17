"""Read-only conversion of Phase 3 graph relationships into similarity features."""

from __future__ import annotations

from collections import defaultdict, deque
from dataclasses import dataclass
from types import MappingProxyType
from typing import Mapping

from app.analytics.graph_projection import GraphProjection, ProjectedEdge
from app.schemas.responses import GraphPayload
from app.schemas.similarity import NodeFeatures, SimilarityNodeType


@dataclass(frozen=True)
class GraphFeatureIndex:
    """Immutable graph projection with helpers for deriving node features."""

    projection: GraphProjection
    enclosing_file_by_node: Mapping[str, str]

    def features_for(self, node_id: str) -> NodeFeatures:
        node = self.projection.node(node_id)
        if node is None:
            raise ValueError(f"Unknown graph node ID: {node_id}")
        if node.type not in {"function", "file", "module"}:
            raise ValueError(f"Unsupported similarity node type: {node.type}")

        if node.type == "function":
            features = self._function_features(node_id)
        elif node.type == "file":
            features = self._file_features(node_id)
        else:
            features = self._module_features(node_id)
        return NodeFeatures(nodeId=node_id, nodeType=node.type, features=features)

    def _function_features(self, node_id: str) -> dict[str, set[str]]:
        return {
            "calledNodes": {
                _token("call", edge.target)
                for edge in self.projection.outgoing_edges(node_id)
                if edge.type == "CALLS"
            },
            "callerNodes": {
                _token("caller", edge.source)
                for edge in self.projection.incoming_edges(node_id)
                if edge.type == "CALLS"
            },
            "neighborNodes": _neighbor_tokens(self.projection, node_id),
            "enclosingFileImports": {
                _token("import", edge.target)
                for edge in self.projection.outgoing_edges(self.enclosing_file_by_node.get(node_id, ""))
                if edge.type == "IMPORTS"
            },
        }

    def _file_features(self, node_id: str) -> dict[str, set[str]]:
        return {
            "importedModules": {
                _token("import", edge.target)
                for edge in self.projection.outgoing_edges(node_id)
                if edge.type == "IMPORTS" and self.projection.node(edge.target).type == "module"
            },
            "internalDependencies": {
                _token("dependency", edge.target)
                for edge in self.projection.outgoing_edges(node_id)
                if edge.type == "USES" and self.projection.node(edge.target).type == "file"
            },
            "containedSymbols": {
                _token("symbol", edge.source)
                for edge in self.projection.incoming_edges(node_id)
                if edge.type == "BELONGS_TO"
                and self.projection.node(edge.source).type in {"class", "function"}
            },
            "neighborNodes": _neighbor_tokens(self.projection, node_id),
        }

    def _module_features(self, node_id: str) -> dict[str, set[str]]:
        importing_files = {
            edge.source
            for edge in self.projection.incoming_edges(node_id)
            if edge.type == "IMPORTS" and self.projection.node(edge.source).type == "file"
        }
        using_files = {
            file_id
            for edge in self.projection.incoming_edges(node_id)
            if edge.type == "CALLS"
            for file_id in [self.enclosing_file_by_node.get(edge.source)]
            if file_id is not None
        }
        return {
            "importingFiles": {_token("importer", file_id) for file_id in importing_files},
            "usingFiles": {_token("user", file_id) for file_id in using_files},
            "neighborNodes": _neighbor_tokens(self.projection, node_id),
        }


class GraphFeatureExtractor:
    """Build deterministic, immutable indexes for Phase 6 graph features."""

    def build_index(self, graph: GraphPayload | GraphProjection) -> GraphFeatureIndex:
        projection = graph if isinstance(graph, GraphProjection) else GraphProjection.from_payload(graph)
        return GraphFeatureIndex(
            projection=projection,
            enclosing_file_by_node=MappingProxyType(_find_enclosing_files(projection)),
        )

    def features_for(self, graph: GraphPayload | GraphProjection, node_id: str) -> NodeFeatures:
        return self.build_index(graph).features_for(node_id)


def _find_enclosing_files(projection: GraphProjection) -> dict[str, str]:
    """Find a node's containing file by walking child-to-parent containment edges."""
    result: dict[str, str] = {}
    for node_id in projection.nodes_by_id:
        visited = {node_id}
        queue = deque([node_id])
        while queue:
            current = queue.popleft()
            current_node = projection.node(current)
            if current != node_id and current_node and current_node.type == "file":
                result[node_id] = current
                break
            for edge in projection.outgoing_edges(current):
                if edge.type == "BELONGS_TO" and edge.target not in visited:
                    visited.add(edge.target)
                    queue.append(edge.target)
    return result


def _neighbor_tokens(projection: GraphProjection, node_id: str) -> set[str]:
    neighbors = {
        edge.target for edge in projection.outgoing_edges(node_id)
    } | {
        edge.source for edge in projection.incoming_edges(node_id)
    }
    return {_token("neighbor", neighbor) for neighbor in neighbors}


def _token(feature_kind: str, node_id: str) -> str:
    return f"{feature_kind}:{node_id}"
