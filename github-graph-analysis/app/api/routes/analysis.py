from fastapi import APIRouter

from app.schemas.requests import AnalysisJobRequest
from app.schemas.responses import AnalysisJobResponse

router = APIRouter(prefix="/internal/v1")


@router.post("/analysis-jobs", response_model=AnalysisJobResponse)
def analyze_repository(request: AnalysisJobRequest) -> AnalysisJobResponse:
    return AnalysisJobResponse(
        ingestionJobId=request.ingestionJobId,
        status="COMPLETED",
        snapshot={"branchName": "unknown", "commitSha": "unknown"},
        summary={
            "totalFiles": 0,
            "totalDirectories": 0,
            "languageSummary": {}
        },
        files=[],
        manifests=[],
        graph={"nodes": [], "edges": []}
    )
