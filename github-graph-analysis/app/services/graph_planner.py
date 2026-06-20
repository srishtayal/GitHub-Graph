import hashlib
import json
from collections import defaultdict
from typing import Any, Iterable

from app.schemas.responses import (
    ApiRouteMetadata,
    ClassMetadata,
    FileMetadata,
    FunctionMetadata,
    GraphEdge,
    GraphNode,
    GraphPayload,
    InheritanceMetadata,
    MethodCallMetadata,
    ModuleDependencyMetadata,
)
from app.services.static_code_extractor import ParsedRepository


class GraphBuilder:
    def __init__(self, repository_id: str) -> None:
        self.repository_id = repository_id
        self.nodes: dict[str, GraphNode] = {}
        self.edges: dict[tuple[str, str, str, str], GraphEdge] = {}

    def node_id(self, node_type: str, *parts: str) -> str:
        return _node_id(self.repository_id, node_type, *parts)

    def add_node(self, node_id: str, node_type: str, label: str, **properties: Any) -> str:
        clean_properties = _clean_properties(properties)
        existing = self.nodes.get(node_id)
        if existing:
            existing.properties.update(clean_properties)
            return node_id

        self.nodes[node_id] = GraphNode(
            id=node_id,
            type=node_type,
            label=label,
            properties=clean_properties,
        )
        return node_id

    def add_edge(self, source: str, target: str, edge_type: str, **properties: Any) -> None:
        if source not in self.nodes or target not in self.nodes:
            return

        clean_properties = _clean_properties(properties)
        property_key = json.dumps(clean_properties, sort_keys=True, separators=(",", ":"))
        key = (source, target, edge_type, property_key)
        if key in self.edges:
            return

        edge_id = _edge_id(source, target, edge_type, property_key)
        self.edges[key] = GraphEdge(
            id=edge_id,
            source=source,
            target=target,
            type=edge_type,
            properties=clean_properties,
        )

    def payload(self) -> GraphPayload:
        return GraphPayload(
            nodes=sorted(self.nodes.values(), key=lambda item: item.id),
            edges=sorted(self.edges.values(), key=lambda item: item.id),
        )


def build_graph_payload(
    repository_id: str,
    github_url: str,
    files: Iterable[FileMetadata],
    parsed: ParsedRepository,
) -> GraphPayload:
    indexes = _GraphIndexes()
    builder = GraphBuilder(repository_id)

    repo_node_id = builder.add_node(
        builder.node_id("repo"),
        "repo",
        _repo_label(github_url),
        repositoryId=repository_id,
        githubUrl=github_url,
    )

    file_nodes: dict[str, str] = {}
    for file_item in files:
        file_node_id = builder.add_node(
            builder.node_id("file", file_item.relativePath),
            "file",
            file_item.relativePath,
            relativePath=file_item.relativePath,
            fileName=file_item.fileName,
            extension=file_item.extension,
            language=file_item.language,
            sizeBytes=file_item.sizeBytes,
            isBinary=file_item.isBinary,
        )
        file_nodes[file_item.relativePath] = file_node_id
        builder.add_edge(file_node_id, repo_node_id, "BELONGS_TO")

    _add_class_nodes(builder, indexes, file_nodes, parsed.classes)
    _add_function_nodes(builder, indexes, file_nodes, parsed.functions)
    _add_module_dependency_edges(builder, file_nodes, parsed.module_dependencies)
    _add_inheritance_edges(builder, indexes, parsed.inheritance)
    _add_api_nodes(builder, indexes, file_nodes, parsed.api_routes)
    _add_call_edges(builder, indexes, file_nodes, parsed.method_calls)

    return builder.payload()


class _GraphIndexes:
    def __init__(self) -> None:
        self.class_by_file_and_name: dict[tuple[str, str], str] = {}
        self.class_by_name: dict[str, list[str]] = defaultdict(list)
        self.function_by_file_and_name: dict[tuple[str, str], str] = {}
        self.function_by_name: dict[str, list[str]] = defaultdict(list)

    def add_class(self, item: ClassMetadata, node_id: str) -> None:
        self.class_by_file_and_name[(item.relativePath, item.qualifiedName)] = node_id
        self.class_by_file_and_name[(item.relativePath, item.name)] = node_id
        self.class_by_name[item.qualifiedName].append(node_id)
        self.class_by_name[item.name].append(node_id)

    def add_function(self, item: FunctionMetadata, node_id: str) -> None:
        self.function_by_file_and_name[(item.relativePath, item.qualifiedName)] = node_id
        self.function_by_file_and_name[(item.relativePath, item.name)] = node_id
        self.function_by_name[item.qualifiedName].append(node_id)
        self.function_by_name[item.name].append(node_id)


def _add_class_nodes(
    builder: GraphBuilder,
    indexes: _GraphIndexes,
    file_nodes: dict[str, str],
    classes: list[ClassMetadata],
) -> None:
    for item in classes:
        class_node_id = builder.add_node(
            builder.node_id("class", item.relativePath, item.qualifiedName),
            "class",
            item.qualifiedName,
            relativePath=item.relativePath,
            name=item.name,
            qualifiedName=item.qualifiedName,
            bases=item.bases,
            startLine=item.startLine,
            endLine=item.endLine,
        )
        indexes.add_class(item, class_node_id)
        if file_node_id := file_nodes.get(item.relativePath):
            builder.add_edge(class_node_id, file_node_id, "BELONGS_TO")


def _add_function_nodes(
    builder: GraphBuilder,
    indexes: _GraphIndexes,
    file_nodes: dict[str, str],
    functions: list[FunctionMetadata],
) -> None:
    for item in functions:
        function_node_id = builder.add_node(
            builder.node_id("function", item.relativePath, item.qualifiedName, str(item.startLine)),
            "function",
            item.qualifiedName,
            relativePath=item.relativePath,
            name=item.name,
            qualifiedName=item.qualifiedName,
            functionType=item.functionType,
            parentClass=item.parentClass,
            parameters=item.parameters,
            isAsync=item.isAsync,
            startLine=item.startLine,
            endLine=item.endLine,
        )
        indexes.add_function(item, function_node_id)

        parent_node_id = None
        if item.parentClass:
            parent_node_id = _resolve_class(indexes, item.relativePath, item.parentClass)
        parent_node_id = parent_node_id or file_nodes.get(item.relativePath)
        if parent_node_id:
            builder.add_edge(function_node_id, parent_node_id, "BELONGS_TO")


def _add_module_dependency_edges(
    builder: GraphBuilder,
    file_nodes: dict[str, str],
    module_dependencies: list[ModuleDependencyMetadata],
) -> None:
    for item in module_dependencies:
        source_node_id = file_nodes.get(item.sourcePath)
        module_node_id = _module_node(builder, item.targetModule)
        if source_node_id:
            builder.add_edge(
                source_node_id,
                module_node_id,
                "IMPORTS",
                targetModule=item.targetModule,
                dependencyType=item.dependencyType,
                resolvedPath=item.resolvedPath,
            )
            if item.resolvedPath and item.resolvedPath in file_nodes:
                builder.add_edge(
                    source_node_id,
                    file_nodes[item.resolvedPath],
                    "USES",
                    reason="module_dependency",
                    targetModule=item.targetModule,
                )


def _add_inheritance_edges(
    builder: GraphBuilder,
    indexes: _GraphIndexes,
    inheritance: list[InheritanceMetadata],
) -> None:
    for item in inheritance:
        source_node_id = _resolve_class(indexes, item.relativePath, item.childClass)
        if not source_node_id:
            continue

        target_node_id = _resolve_class(indexes, item.relativePath, item.parentClass)
        if not target_node_id:
            target_node_id = _module_node(builder, item.parentClass)

        builder.add_edge(
            source_node_id,
            target_node_id,
            "INHERITS",
            parentClass=item.parentClass,
            startLine=item.startLine,
        )


def _add_api_nodes(
    builder: GraphBuilder,
    indexes: _GraphIndexes,
    file_nodes: dict[str, str],
    api_routes: list[ApiRouteMetadata],
) -> None:
    for item in api_routes:
        api_node_id = builder.add_node(
            builder.node_id("api", item.relativePath, item.httpMethod, item.path, item.handler, str(item.startLine)),
            "api",
            f"{item.httpMethod} {item.path}",
            relativePath=item.relativePath,
            framework=item.framework,
            httpMethod=item.httpMethod,
            path=item.path,
            handler=item.handler,
            startLine=item.startLine,
        )
        if file_node_id := file_nodes.get(item.relativePath):
            builder.add_edge(api_node_id, file_node_id, "BELONGS_TO")

        handler_node_id = _resolve_function(indexes, item.relativePath, item.handler)
        if handler_node_id:
            builder.add_edge(
                api_node_id,
                handler_node_id,
                "USES",
                reason="route_handler",
                httpMethod=item.httpMethod,
                path=item.path,
            )


def _add_call_edges(
    builder: GraphBuilder,
    indexes: _GraphIndexes,
    file_nodes: dict[str, str],
    method_calls: list[MethodCallMetadata],
) -> None:
    for item in method_calls:
        source_node_id = _resolve_function(indexes, item.relativePath, item.caller) if item.caller else None
        source_node_id = source_node_id or file_nodes.get(item.relativePath)
        if not source_node_id:
            continue

        target_node_id = _resolve_call_target(builder, indexes, item)
        if not target_node_id:
            continue

        builder.add_edge(
            source_node_id,
            target_node_id,
            "CALLS",
            expression=item.expression,
            name=item.name,
            receiver=item.receiver,
            startLine=item.startLine,
        )


def _resolve_call_target(
    builder: GraphBuilder,
    indexes: _GraphIndexes,
    item: MethodCallMetadata,
) -> str | None:
    if item.caller and item.receiver == "self":
        caller_class = item.caller.rsplit(".", 1)[0] if "." in item.caller else None
        if caller_class:
            if node_id := _resolve_function(indexes, item.relativePath, f"{caller_class}.{item.name}"):
                return node_id

    if node_id := _resolve_function(indexes, item.relativePath, item.expression):
        return node_id
    if node_id := _resolve_function(indexes, item.relativePath, item.name):
        return node_id

    if item.receiver:
        if node_id := _resolve_class(indexes, item.relativePath, item.receiver):
            return node_id
        root = item.receiver.split(".", 1)[0]
        if root not in {"self", "cls"}:
            return _module_node(builder, root)

    if "." in item.expression:
        root = item.expression.split(".", 1)[0]
        if root not in {"self", "cls"}:
            return _module_node(builder, root)

    return _module_node(builder, item.name)


def _resolve_class(indexes: _GraphIndexes, relative_path: str, name: str | None) -> str | None:
    if not name:
        return None
    if node_id := indexes.class_by_file_and_name.get((relative_path, name)):
        return node_id
    matches = indexes.class_by_name.get(name, [])
    return matches[0] if len(matches) == 1 else None


def _resolve_function(indexes: _GraphIndexes, relative_path: str, name: str | None) -> str | None:
    if not name:
        return None
    if node_id := indexes.function_by_file_and_name.get((relative_path, name)):
        return node_id
    matches = indexes.function_by_name.get(name, [])
    return matches[0] if len(matches) == 1 else None


def _module_node(builder: GraphBuilder, name: str, **properties: Any) -> str:
    label = name or "unknown"
    return builder.add_node(builder.node_id("module", label), "module", label, name=label, **properties)


def _node_id(repository_id: str, node_type: str, *parts: str) -> str:
    identity = json.dumps([repository_id, node_type, *parts], separators=(",", ":"))
    digest = hashlib.sha1(identity.encode("utf-8")).hexdigest()[:20]
    return f"{node_type}:{digest}"


def _edge_id(source: str, target: str, edge_type: str, property_key: str) -> str:
    digest = hashlib.sha1(f"{source}|{target}|{edge_type}|{property_key}".encode("utf-8")).hexdigest()[:16]
    return f"edge:{digest}"


def _repo_label(github_url: str) -> str:
    return github_url.removesuffix(".git").rstrip("/").rsplit("/", 2)[-1]


def _clean_properties(properties: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in properties.items() if value is not None}
