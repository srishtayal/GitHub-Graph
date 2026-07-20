from pydantic import BaseModel, Field

from app.schemas.failure_analysis import FailureInput, HistoricalFailure, LocalizationConfiguration
from app.schemas.responses import GraphPayload
from app.schemas.similarity import FeatureProfile, SimilarityNodeType


class SimilarityConfiguration(BaseModel):
    limit: int = Field(default=10, ge=0)
    profile: FeatureProfile | None = None


class SimilarityRequest(BaseModel):
    graph: GraphPayload
    targetNodeId: str
    configuration: SimilarityConfiguration = Field(default_factory=SimilarityConfiguration)


class ClusterConfiguration(BaseModel):
    threshold: float = Field(default=0.5, ge=0.0, le=1.0)
    profile: FeatureProfile | None = None


class ClusterRequest(BaseModel):
    graph: GraphPayload
    nodeType: SimilarityNodeType
    configuration: ClusterConfiguration = Field(default_factory=ClusterConfiguration)


class LocalizationRequest(BaseModel):
    graph: GraphPayload
    failure: FailureInput
    history: list[HistoricalFailure] = Field(default_factory=list)
    configuration: LocalizationConfiguration = Field(default_factory=LocalizationConfiguration)
