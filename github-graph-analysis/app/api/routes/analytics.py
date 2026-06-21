from typing import Any

from fastapi import APIRouter, HTTPException

from app.schemas.requests import GraphAnalyticsRequest
from app.services.graph_analyzer import analyze_graph

router = APIRouter(prefix="/internal/v1")


@router.post("/graph-analytics")
def run_graph_analytics(request: GraphAnalyticsRequest) -> dict[str, Any]:
    if not request.graph.nodes:
        raise HTTPException(status_code=400, detail="Graph must contain at least one node")

    result = analyze_graph(
        graph=request.graph,
        node_id=request.nodeId,
        max_depth=request.maxDepth,
    )

    if result.get("error") == "node_not_found":
        raise HTTPException(status_code=404, detail=result["message"])

    return result
