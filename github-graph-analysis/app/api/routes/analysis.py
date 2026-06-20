from fastapi import APIRouter

from app.schemas.requests import AnalysisJobRequest
from app.schemas.responses import AnalysisJobResponse
from app.services.repo_scanner import scan_repository

router = APIRouter(prefix="/internal/v1")


@router.post("/analysis-jobs", response_model=AnalysisJobResponse)
def analyze_repository(request: AnalysisJobRequest) -> AnalysisJobResponse:
    directories, files, parsed = scan_repository(request.localPath)
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
            "languageSummary": language_summary,
            "totalClasses": len(parsed.classes),
            "totalFunctions": len(parsed.functions),
            "totalMethodCalls": len(parsed.method_calls),
            "totalApiRoutes": len(parsed.api_routes),
            "totalModuleDependencies": len(parsed.module_dependencies),
        },
        directories=directories,
        files=files,
        codeFiles=parsed.code_files,
        symbols=parsed.symbols,
        imports=parsed.imports,
        classes=parsed.classes,
        functions=parsed.functions,
        methodCalls=parsed.method_calls,
        inheritance=parsed.inheritance,
        apiRoutes=parsed.api_routes,
        moduleDependencies=parsed.module_dependencies,
        graph={"nodes": [], "edges": []}
    )
