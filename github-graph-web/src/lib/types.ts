export type CreateIngestionResponse = {
  jobId: string;
  repositoryId: string;
  status: string;
  reused: boolean;
};

export type IngestionJob = {
  jobId: string;
  repositoryId: string | null;
  status: string;
  errorMessage: string | null;
  errorCategory?: string | null;
  retryCount: number;
  createdAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
};

export type RepositorySnapshot = {
  snapshotId: string;
  ingestionJobId: string;
  branchName: string;
  commitSha: string;
  totalFiles: number;
  totalDirectories: number;
  languageSummary: Record<string, number>;
  analyzedAt: string;
};

export type AuthUser = {
  userId: string;
  email: string;
  displayName: string;
};

export type AuthResponse = {
  accessToken: string;
  user: AuthUser;
  expiresInSeconds: number;
};

export type RepositoryCatalog = {
  repositories: RepositorySummary[];
};

export type SnapshotHistory = {
  snapshots: Array<{
    snapshotId: string;
    ingestionJobId: string;
    branchName: string | null;
    commitSha: string | null;
    commitMessage: string | null;
    commitAuthor: string | null;
    committedAt: string | null;
    analyzedAt: string;
    totalFiles: number;
    totalDirectories: number;
    languageSummary: Record<string, number>;
  }>;
};

export type RepositorySummary = {
  repositoryId: string;
  githubUrl: string;
  owner: string;
  name: string;
  status: string;
  latestSnapshot: RepositorySnapshot | null;
};

export type RepositoryFile = {
  fileId: string;
  relativePath: string;
  language: string | null;
  sizeBytes: number;
};

export type RepositoryFileList = {
  items: RepositoryFile[];
};

export type RepositorySymbol = {
  symbolId: string;
  fileId: string | null;
  symbolType: string;
  name: string;
  qualifiedName: string | null;
  language: string | null;
  startLine: number | null;
  endLine: number | null;
};

export type RepositorySymbolList = {
  items: RepositorySymbol[];
};

export type GraphNode = {
  id: string;
  type: string;
  label: string;
  properties: Record<string, unknown>;
};

export type GraphEdge = {
  id: string;
  source: string;
  target: string;
  type: string;
  properties: Record<string, unknown>;
};

export type RepositoryGraph = {
  nodes: GraphNode[];
  edges: GraphEdge[];
};

export type GraphProjectionNode = {
  id: string;
  displayName: string;
  level: string;
  category: string;
  counts: {
    files: number;
    classes: number;
    functions: number;
    routes: number;
  };
  incomingDependencyCount: number;
  outgoingDependencyCount: number;
  criticalityScore: number;
  childCount: number;
  representatives: Array<{
    id: string;
    displayName: string;
    type: string;
  }>;
  underlyingNodeIds: string[];
  expandable: boolean;
};

export type GraphProjectionEdge = {
  id: string;
  source: string;
  target: string;
  type: string;
  totalRelationshipCount: number;
  countsByType: Record<string, number>;
  underlyingEdgeIds: string[];
};

export type GraphProjection = {
  repositoryId: string;
  snapshotId: string;
  level: "OVERVIEW" | "COMPONENT" | "FILE" | "NEIGHBORHOOD";
  rootId: string | null;
  suggestedMaximumNodes: number;
  truncated: boolean;
  totals: {
    rawNodeCount: number;
    rawEdgeCount: number;
    projectedNodeCount: number;
    projectedEdgeCount: number;
  };
  nodes: GraphProjectionNode[];
  edges: GraphProjectionEdge[];
};

export type RepositoryAnalysis = {
  ingestionJobId: string;
  status: string;
  summary: {
    totalFiles: number;
    totalDirectories: number;
    languageSummary: Record<string, number>;
    totalClasses: number;
    totalFunctions: number;
    totalMethodCalls: number;
    totalApiRoutes: number;
    totalModuleDependencies: number;
    totalGraphNodes: number;
    totalGraphEdges: number;
  };
  codeFiles: Array<{
    relativePath: string;
    language: string | null;
    classes: string[];
    functions: string[];
    imports: string[];
  }>;
  classes: Array<{
    relativePath: string;
    name: string;
    qualifiedName: string;
    startLine: number;
    endLine: number;
    bases: string[];
  }>;
  functions: Array<{
    relativePath: string;
    name: string;
    qualifiedName: string;
    functionType: string;
    parentClass: string | null;
    startLine: number;
    endLine: number;
    parameters: string[];
    isAsync: boolean;
  }>;
  methodCalls: Array<{
    relativePath: string;
    name: string;
    startLine: number;
    caller: string | null;
    receiver: string | null;
    expression: string;
  }>;
  inheritance: Array<{
    relativePath: string;
    childClass: string;
    parentClass: string;
    startLine: number;
  }>;
  apiRoutes: Array<{
    relativePath: string;
    framework: string;
    httpMethod: string;
    path: string;
    handler: string;
    startLine: number;
  }>;
  moduleDependencies: Array<{
    sourcePath: string;
    targetModule: string;
    resolvedPath: string | null;
    dependencyType: string;
  }>;
};

export type AnalyticsNode = GraphNode;

export type TraversalNode = {
  node: AnalyticsNode;
  depth: number;
  predecessorNodeId: string | null;
  viaEdgeType: string | null;
};

export type DependencyPathResponse = {
  repositoryId: string;
  startNodeId: string;
  traversalOrder: TraversalNode[];
};

export type ImpactAnalysisResponse = {
  repositoryId: string;
  startNodeId: string;
  totalAffectedNodes: number;
  affectedNodes: TraversalNode[];
};

export type ConnectedComponentsResponse = {
  repositoryId: string;
  totalComponents: number;
  components: Array<{
    id: string;
    size: number;
    nodes: AnalyticsNode[];
  }>;
};

export type CycleDetectionResponse = {
  repositoryId: string;
  hasCycles: boolean;
  totalCycles: number;
  cycles: Array<{ nodeIds: string[] }>;
};

export type TopologicalOrderResponse = {
  repositoryId: string;
  acyclic: boolean;
  message: string;
  order: AnalyticsNode[];
  cycles: string[][];
};

export type CriticalNodesResponse = {
  repositoryId: string;
  totalReturned: number;
  nodes: Array<{
    node: AnalyticsNode;
    inDegree: number;
    outDegree: number;
    totalDegree: number;
    degreeCentrality: number;
  }>;
};

export type FeatureSimilarity = {
  score: number;
  matchedFeatures: string[];
};

export type SimilarityRanking = {
  targetNodeId: string;
  nodeType: string;
  results: Array<{
    targetNodeId: string;
    candidateNodeId: string;
    nodeType: string;
    score: number;
    featureScores: Record<string, FeatureSimilarity>;
    clusterId: string | null;
  }>;
};

export type ClusterResult = {
  nodeType: string;
  threshold: number;
  clusters: Array<{
    clusterId: string;
    nodeType: string;
    threshold: number;
    memberNodeIds: string[];
    links: Array<{
      sourceNodeId: string;
      targetNodeId: string;
      score: number;
    }>;
  }>;
};

export type BugLocalizationResult = {
  resolvedFailurePath: {
    nodeIds: string[];
    stackFrameNodeIds: string[];
    errorSignature: {
      exceptionType: string | null;
      messageFingerprint: string | null;
    };
    unresolvedReferences: Array<{
      kind: string;
      value: string;
      detail: string | null;
    }>;
  };
  impactedNodeIds: string[];
  similarPastFailures: Array<{
    failureId: string;
    similarity: number;
  }>;
  suspectedRootCauses: Array<{
    nodeId: string;
    score: number;
    confidence: string;
    reasons: Array<{
      kind: string;
      weight: number;
      detail: string | null;
    }>;
  }>;
  reasoningMetadata: Record<string, unknown>;
};

export type FailureRecord = {
  failureId: string;
  repositoryId: string;
  snapshotId: string;
  status: string;
  failingNodeId: string | null;
  errorLog: string | null;
  stackTrace: string | null;
  errorSignature: {
    exceptionType: string | null;
    messageFingerprint: string | null;
  };
  resolvedFailurePathNodeIds: string[];
  confirmedRootCauseNodeIds: string[];
  resolutionNotes: string | null;
  occurredAt: string;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
  localization: BugLocalizationResult | null;
};

export type FailureCollection = {
  repositoryId: string;
  snapshotId: string | null;
  failures: FailureRecord[];
};

export type ExplanationResponse = {
  intent: string;
  answer: string;
  supportingEvidence: Array<{
    evidenceId: string;
    sourceType: string;
    rationale: string;
  }>;
  referencedNodeIds: string[];
  referencedEdgeIds: string[];
  confidence: "high" | "medium" | "low" | "insufficient";
  limitations: string[];
  followUpSuggestions: string[];
  snapshotMetadata: {
    repositoryId: string;
    snapshotId: string;
    branchName: string | null;
    commitSha: string | null;
  };
  modelMetadata: {
    provider: string;
    model: string;
    promptVersion: string;
    orchestrationVersion: string;
  };
};

export type RepositoryWorkspaceData = {
  summary: RepositorySummary;
  files: RepositoryFileList;
  symbols: RepositorySymbolList;
  analysis: RepositoryAnalysis;
  graph: RepositoryGraph;
  critical: CriticalNodesResponse;
  components: ConnectedComponentsResponse;
  cycles: CycleDetectionResponse;
  topologicalOrder: TopologicalOrderResponse;
  failures: FailureCollection;
};
