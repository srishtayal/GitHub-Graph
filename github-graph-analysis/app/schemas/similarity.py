from typing import Literal

from pydantic import BaseModel, Field, model_validator


SimilarityNodeType = Literal["function", "file", "module", "failurePath"]
FeatureValues = dict[str, set[str]]


class FeatureProfile(BaseModel):
    """Weights used to aggregate Jaccard scores for one comparison type."""

    name: str
    weights: dict[str, float]

    @model_validator(mode="after")
    def validate_weights(self) -> "FeatureProfile":
        if not self.weights:
            raise ValueError("Feature profiles must define at least one feature weight")
        if any(weight < 0 for weight in self.weights.values()):
            raise ValueError("Feature weights must be non-negative")
        if not any(weight > 0 for weight in self.weights.values()):
            raise ValueError("Feature profiles must define at least one positive feature weight")
        return self


class SimilarityProfiles(BaseModel):
    function: FeatureProfile
    file: FeatureProfile
    module: FeatureProfile
    failurePath: FeatureProfile


DEFAULT_SIMILARITY_PROFILES = SimilarityProfiles(
    function=FeatureProfile(
        name="function",
        weights={
            "calledNodes": 0.40,
            "neighborNodes": 0.25,
            "callerNodes": 0.20,
            "enclosingFileImports": 0.15,
        },
    ),
    file=FeatureProfile(
        name="file",
        weights={
            "importedModules": 0.40,
            "internalDependencies": 0.30,
            "containedSymbols": 0.20,
            "neighborNodes": 0.10,
        },
    ),
    module=FeatureProfile(
        name="module",
        weights={
            "importingFiles": 0.45,
            "usingFiles": 0.35,
            "neighborNodes": 0.20,
        },
    ),
    failurePath=FeatureProfile(
        name="failurePath",
        weights={
            "pathNodes": 0.45,
            "touchedFiles": 0.20,
            "dependencies": 0.20,
            "errorSignature": 0.15,
        },
    ),
)


class NodeFeatures(BaseModel):
    nodeId: str
    nodeType: SimilarityNodeType
    features: FeatureValues = Field(default_factory=dict)


class FeatureSimilarity(BaseModel):
    score: float = Field(ge=0.0, le=1.0)
    matchedFeatures: list[str] = Field(default_factory=list)


class SimilarityResult(BaseModel):
    targetNodeId: str
    candidateNodeId: str
    nodeType: SimilarityNodeType
    score: float = Field(ge=0.0, le=1.0)
    featureScores: dict[str, FeatureSimilarity] = Field(default_factory=dict)
    clusterId: str | None = None


class SimilarityRanking(BaseModel):
    targetNodeId: str
    nodeType: SimilarityNodeType
    results: list[SimilarityResult]


class SimilarityLink(BaseModel):
    sourceNodeId: str
    targetNodeId: str
    score: float = Field(ge=0.0, le=1.0)


class SimilarityCluster(BaseModel):
    clusterId: str
    nodeType: SimilarityNodeType
    threshold: float = Field(ge=0.0, le=1.0)
    memberNodeIds: list[str]
    links: list[SimilarityLink] = Field(default_factory=list)


class ClusterResult(BaseModel):
    nodeType: SimilarityNodeType
    threshold: float = Field(ge=0.0, le=1.0)
    clusters: list[SimilarityCluster]
