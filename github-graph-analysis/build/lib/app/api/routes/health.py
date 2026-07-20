from fastapi import APIRouter

router = APIRouter(prefix="/internal/v1")


@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "github-graph-analysis"}
