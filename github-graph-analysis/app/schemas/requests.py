from pydantic import BaseModel, HttpUrl


class AnalysisJobRequest(BaseModel):
    ingestionJobId: str
    repositoryId: str
    localPath: str
    githubUrl: HttpUrl
