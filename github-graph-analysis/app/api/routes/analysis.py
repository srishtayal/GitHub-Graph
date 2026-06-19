from fastapi import APIRouter

from app.schemas.requests import AnalysisJobRequest
from app.schemas.responses import AnalysisJobResponse
from app.services.repo_scanner import scan_repository

router = APIRouter(prefix="/internal/v1")


@router.post("/analysis-jobs", response_model=AnalysisJobResponse)
def analyze_repository(request: AnalysisJobRequest) -> AnalysisJobResponse:
    directories, files, symbols, imports = scan_repository(request.localPath)
    language_summary: dict[str, int] = {}
    for item in files:
        if item.language:
            language_summary[item.language] = language_summary.get(item.language, 0) + 1

    return AnalysisJobResponse(
        ingestionJobId=request.ingestionJobId,
        status="COMPLETED",
        snapshot={"branchName": "unknown", "commitSha": "unknown"},
        summary={
            "totalFiles": len(files),
            "totalDirectories": len(directories),
            "languageSummary": language_summary
        },
        directories=directories,
        files=files,
        symbols=symbols,
        imports=imports,
        graph={"nodes": [], "edges": []}
    )
