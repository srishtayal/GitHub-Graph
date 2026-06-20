from typing import Any

from pydantic import BaseModel, Field


class SnapshotSummary(BaseModel):
    branchName: str
    commitSha: str


class AnalysisSummary(BaseModel):
    totalFiles: int
    totalDirectories: int
    languageSummary: dict[str, int]
    totalClasses: int
    totalFunctions: int
    totalMethodCalls: int
    totalApiRoutes: int
    totalModuleDependencies: int


class FileMetadata(BaseModel):
    relativePath: str
    fileName: str
    extension: str | None = None
    language: str | None = None
    sizeBytes: int
    isBinary: bool


class DirectoryMetadata(BaseModel):
    relativePath: str
    name: str
    parentPath: str | None = None


class SymbolMetadata(BaseModel):
    relativePath: str
    symbolType: str
    name: str
    qualifiedName: str | None = None
    language: str | None = None
    startLine: int | None = None
    endLine: int | None = None
    parentSymbolName: str | None = None


class ImportMetadata(BaseModel):
    relativePath: str
    importValue: str
    importType: str | None = None
    resolvedPath: str | None = None


class ClassMetadata(BaseModel):
    relativePath: str
    name: str
    qualifiedName: str
    bases: list[str]
    startLine: int
    endLine: int


class FunctionMetadata(BaseModel):
    relativePath: str
    name: str
    qualifiedName: str
    functionType: str
    parentClass: str | None = None
    parameters: list[str]
    isAsync: bool
    startLine: int
    endLine: int


class MethodCallMetadata(BaseModel):
    relativePath: str
    caller: str | None = None
    name: str
    receiver: str | None = None
    expression: str
    startLine: int


class InheritanceMetadata(BaseModel):
    relativePath: str
    childClass: str
    parentClass: str
    startLine: int


class ApiRouteMetadata(BaseModel):
    relativePath: str
    framework: str
    httpMethod: str
    path: str
    handler: str
    startLine: int


class ModuleDependencyMetadata(BaseModel):
    sourcePath: str
    targetModule: str
    resolvedPath: str | None = None
    dependencyType: str


class CodeFileMetadata(BaseModel):
    relativePath: str
    language: str
    classes: list[ClassMetadata]
    functions: list[FunctionMetadata]
    imports: list[ImportMetadata]
    methodCalls: list[MethodCallMetadata]
    inheritance: list[InheritanceMetadata]
    apiRoutes: list[ApiRouteMetadata]
    moduleDependencies: list[ModuleDependencyMetadata]


class GraphNode(BaseModel):
    id: str
    type: str
    label: str
    properties: dict[str, Any] = Field(default_factory=dict)


class GraphEdge(BaseModel):
    id: str
    source: str
    target: str
    type: str
    properties: dict[str, Any] = Field(default_factory=dict)


class GraphPayload(BaseModel):
    nodes: list[GraphNode]
    edges: list[GraphEdge]


class AnalysisJobResponse(BaseModel):
    ingestionJobId: str
    status: str
    snapshot: SnapshotSummary
    summary: AnalysisSummary
    directories: list[DirectoryMetadata]
    files: list[FileMetadata]
    codeFiles: list[CodeFileMetadata]
    symbols: list[SymbolMetadata]
    imports: list[ImportMetadata]
    classes: list[ClassMetadata]
    functions: list[FunctionMetadata]
    methodCalls: list[MethodCallMetadata]
    inheritance: list[InheritanceMetadata]
    apiRoutes: list[ApiRouteMetadata]
    moduleDependencies: list[ModuleDependencyMetadata]
    graph: GraphPayload
