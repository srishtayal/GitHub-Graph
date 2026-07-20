from fastapi import APIRouter, HTTPException

from app.schemas.failure_analysis import BugLocalizationResult
from app.schemas.intelligence import ClusterRequest, LocalizationRequest, SimilarityRequest
from app.schemas.similarity import ClusterResult, SimilarityRanking
from app.services.bug_localizer import BugLocalizer
from app.services.similarity_clustering import SimilarityClusterer
from app.services.similarity_engine import SimilarityEngine

router = APIRouter(prefix="/internal/v1/intelligence")

similarity_engine = SimilarityEngine()
similarity_clusterer = SimilarityClusterer()
bug_localizer = BugLocalizer()


@router.post("/similarity", response_model=SimilarityRanking)
def rank_similarity(request: SimilarityRequest) -> SimilarityRanking:
    try:
        return similarity_engine.rank_similar(
            request.graph,
            request.targetNodeId,
            limit=request.configuration.limit,
            profile=request.configuration.profile,
        )
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error


@router.post("/clusters", response_model=ClusterResult)
def cluster_similarity(request: ClusterRequest) -> ClusterResult:
    try:
        return similarity_clusterer.cluster(
            request.graph,
            request.nodeType,
            threshold=request.configuration.threshold,
            profile=request.configuration.profile,
        )
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error


@router.post("/localize", response_model=BugLocalizationResult)
def localize_failure(request: LocalizationRequest) -> BugLocalizationResult:
    try:
        return bug_localizer.localize(
            request.graph,
            request.failure,
            request.history,
            request.configuration,
        )
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
