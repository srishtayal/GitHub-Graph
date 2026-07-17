from datetime import datetime

from pydantic import BaseModel, Field, model_validator


class ErrorSignature(BaseModel):
    exceptionType: str | None = None
    messageFingerprint: str | None = None


class FailureInput(BaseModel):
    repositoryId: str
    failingNodeId: str | None = None
    errorLog: str | None = None
    stackTrace: str | None = None
    failurePathNodeIds: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_evidence(self) -> "FailureInput":
        if not any((self.failingNodeId, self.errorLog, self.stackTrace, self.failurePathNodeIds)):
            raise ValueError("A failure input must include at least one source of failure evidence")
        return self


class UnresolvedReference(BaseModel):
    kind: str
    value: str
    detail: str | None = None


class ResolvedFailurePath(BaseModel):
    nodeIds: list[str] = Field(default_factory=list)
    errorSignature: ErrorSignature = Field(default_factory=ErrorSignature)
    unresolvedReferences: list[UnresolvedReference] = Field(default_factory=list)


class HistoricalFailure(BaseModel):
    failureId: str
    repositoryId: str
    occurredAt: datetime
    failurePathNodeIds: list[str] = Field(default_factory=list)
    errorSignature: ErrorSignature = Field(default_factory=ErrorSignature)
    confirmedRootCauseNodeIds: list[str] = Field(default_factory=list)
    metadata: dict[str, object] = Field(default_factory=dict)


class SimilarPastFailure(BaseModel):
    failureId: str
    similarity: float = Field(ge=0.0, le=1.0)


class RootCauseReason(BaseModel):
    kind: str
    weight: float = Field(ge=0.0, le=1.0)
    detail: str | None = None


class RootCauseCandidate(BaseModel):
    nodeId: str
    score: float = Field(ge=0.0, le=1.0)
    confidence: str
    reasons: list[RootCauseReason] = Field(default_factory=list)


class LocalizationConfiguration(BaseModel):
    maxTraversalDepth: int = Field(default=2, ge=0)
    maxPastFailures: int = Field(default=10, ge=1)
    pathEvidenceWeight: float = Field(default=0.35, ge=0.0)
    stackEvidenceWeight: float = Field(default=0.30, ge=0.0)
    historyEvidenceWeight: float = Field(default=0.20, ge=0.0)
    structuralEvidenceWeight: float = Field(default=0.10, ge=0.0)
    criticalityEvidenceWeight: float = Field(default=0.05, ge=0.0)

    @model_validator(mode="after")
    def validate_weights(self) -> "LocalizationConfiguration":
        weights = (
            self.pathEvidenceWeight,
            self.stackEvidenceWeight,
            self.historyEvidenceWeight,
            self.structuralEvidenceWeight,
            self.criticalityEvidenceWeight,
        )
        if not any(weight > 0 for weight in weights):
            raise ValueError("Localization configuration must define at least one positive evidence weight")
        return self


class BugLocalizationResult(BaseModel):
    resolvedFailurePath: ResolvedFailurePath
    similarPastFailures: list[SimilarPastFailure] = Field(default_factory=list)
    suspectedRootCauses: list[RootCauseCandidate] = Field(default_factory=list)
    reasoningMetadata: dict[str, object] = Field(default_factory=dict)
