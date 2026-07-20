from pydantic import BaseModel, Field, HttpUrl

from app.schemas.responses import GraphPayload


class AnalysisJobRequest(BaseModel):
    ingestionJobId: str
    repositoryId: str
    localPath: str
    githubUrl: HttpUrl


class GraphAnalyticsRequest(BaseModel):
    graph: GraphPayload
    nodeId: str | None = None
    maxDepth: int = Field(default=10, ge=1, le=50)
