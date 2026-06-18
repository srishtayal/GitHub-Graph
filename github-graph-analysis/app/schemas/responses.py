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


class ManifestMetadata(BaseModel):
    manifestType: str
    relativePath: str
    metadata: dict[str, Any]


class GraphPayload(BaseModel):
    nodes: list[dict[str, Any]]
    edges: list[dict[str, Any]]


class AnalysisJobResponse(BaseModel):
    ingestionJobId: str
    status: str
    snapshot: SnapshotSummary
    summary: AnalysisSummary
    files: list[FileMetadata]
    manifests: list[ManifestMetadata]
    graph: GraphPayload
