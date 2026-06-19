from typing import Any

from pydantic import BaseModel


class SnapshotSummary(BaseModel):
    branchName: str
    commitSha: str


class AnalysisSummary(BaseModel):
    totalFiles: int
    totalDirectories: int
    languageSummary: dict[str, int]


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


class GraphPayload(BaseModel):
    nodes: list[dict[str, Any]]
    edges: list[dict[str, Any]]


class AnalysisJobResponse(BaseModel):
    ingestionJobId: str
    status: str
    snapshot: SnapshotSummary
    summary: AnalysisSummary
    directories: list[DirectoryMetadata]
    files: list[FileMetadata]
    symbols: list[SymbolMetadata]
    imports: list[ImportMetadata]
    graph: GraphPayload
