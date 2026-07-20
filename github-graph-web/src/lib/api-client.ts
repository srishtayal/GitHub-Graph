import { env } from "./env";
import type {
  BugLocalizationResult,
  AuthResponse,
  AuthUser,
  ClusterResult,
  ConnectedComponentsResponse,
  CreateIngestionResponse,
  CriticalNodesResponse,
  CycleDetectionResponse,
  DependencyPathResponse,
  ExplanationResponse,
  FailureCollection,
  FailureRecord,
  ImpactAnalysisResponse,
  IngestionJob,
  RepositoryAnalysis,
  RepositoryCatalog,
  RepositoryFileList,
  RepositoryGraph,
  RepositorySummary,
  RepositorySymbolList,
  RepositoryWorkspaceData,
  SimilarityRanking,
  SnapshotHistory,
  TopologicalOrderResponse
} from "./types";

type JsonPayload = Record<string, unknown>;

function authHeaders(): HeadersInit {
  if (typeof window === "undefined") return {};
  const token = window.localStorage.getItem("github-graph-access-token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  fallbackMessage = "The request could not be completed."
): Promise<T> {
  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    cache: "no-store",
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...authHeaders(),
      ...options.headers
    }
  });

  if (!response.ok) {
    let message = fallbackMessage;
    try {
      const error = (await response.json()) as { message?: string; detail?: string };
      message = error.message ?? error.detail ?? message;
    } catch {
      // Preserve the stable user-facing fallback when the body is not JSON.
    }
    throw new Error(message);
  }

  return response.json() as Promise<T>;
}

export async function downloadReport(repositoryId: string, format: "json" | "pdf"): Promise<void> {
  const response = await fetch(`${env.apiBaseUrl}/api/v1/repositories/${encodeURIComponent(repositoryId)}/exports/${format}`, {
    headers: authHeaders()
  });
  if (!response.ok) throw new Error("Unable to export this repository report.");
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `github-graph-report.${format}`;
  link.click();
  URL.revokeObjectURL(url);
}

export function register(payload: { email: string; password: string; displayName: string }): Promise<AuthResponse> {
  return apiRequest<AuthResponse>("/api/v1/auth/register", { method: "POST", body: JSON.stringify(payload) }, "Unable to create account.");
}

export function login(payload: { email: string; password: string }): Promise<AuthResponse> {
  return apiRequest<AuthResponse>("/api/v1/auth/login", { method: "POST", body: JSON.stringify(payload) }, "Unable to sign in.");
}

export function getCurrentUser(): Promise<AuthUser> {
  return apiRequest<AuthUser>("/api/v1/auth/me", {}, "Unable to load account.");
}

export function getSavedRepositories(): Promise<RepositoryCatalog> {
  return apiRequest<RepositoryCatalog>("/api/v1/repositories", {}, "Unable to load saved repositories.");
}

export function getSnapshotHistory(repositoryId: string): Promise<SnapshotHistory> {
  return apiRequest<SnapshotHistory>(`/api/v1/repositories/${encodeURIComponent(repositoryId)}/snapshots`, {}, "Unable to load analysis history.");
}

function repositoryQuery(repositoryId: string): string {
  return new URLSearchParams({ repositoryId }).toString();
}

export function createIngestion(githubUrl: string): Promise<CreateIngestionResponse> {
  return apiRequest<CreateIngestionResponse>(
    "/api/v1/repositories/ingestions",
    {
      method: "POST",
      body: JSON.stringify({ githubUrl })
    },
    "Unable to start repository ingestion."
  );
}

export function getIngestionJob(jobId: string): Promise<IngestionJob> {
  return apiRequest<IngestionJob>(
    `/api/v1/ingestion-jobs/${encodeURIComponent(jobId)}`,
    {},
    "Unable to fetch the ingestion job."
  );
}

export function retryIngestion(jobId: string): Promise<CreateIngestionResponse> {
  return apiRequest<CreateIngestionResponse>(`/api/v1/ingestion-jobs/${encodeURIComponent(jobId)}/retry`, { method: "POST" }, "Unable to retry repository ingestion.");
}

export function getRepositorySummary(repositoryId: string): Promise<RepositorySummary> {
  return apiRequest<RepositorySummary>(`/api/v1/repositories/${repositoryId}`);
}

export function getRepositoryFiles(repositoryId: string): Promise<RepositoryFileList> {
  return apiRequest<RepositoryFileList>(`/api/v1/repositories/${repositoryId}/files`);
}

export function getRepositorySymbols(repositoryId: string): Promise<RepositorySymbolList> {
  return apiRequest<RepositorySymbolList>(`/api/v1/repositories/${repositoryId}/symbols`);
}

export function getRepositoryAnalysis(repositoryId: string): Promise<RepositoryAnalysis> {
  return apiRequest<RepositoryAnalysis>(`/api/v1/repositories/${repositoryId}/analysis`);
}

export function getRepositoryGraph(repositoryId: string): Promise<RepositoryGraph> {
  return apiRequest<RepositoryGraph>(`/api/v1/repositories/${repositoryId}/graph`);
}

export function getCriticalNodes(repositoryId: string, limit = 20): Promise<CriticalNodesResponse> {
  return apiRequest<CriticalNodesResponse>(
    `/api/v1/analytics/critical?${repositoryQuery(repositoryId)}&limit=${limit}`
  );
}

export function getComponents(repositoryId: string): Promise<ConnectedComponentsResponse> {
  return apiRequest<ConnectedComponentsResponse>(
    `/api/v1/analytics/components?${repositoryQuery(repositoryId)}`
  );
}

export function getCycles(repositoryId: string): Promise<CycleDetectionResponse> {
  return apiRequest<CycleDetectionResponse>(
    `/api/v1/analytics/cycles?${repositoryQuery(repositoryId)}`
  );
}

export function getTopologicalOrder(repositoryId: string): Promise<TopologicalOrderResponse> {
  return apiRequest<TopologicalOrderResponse>(
    `/api/v1/analytics/topological-order?${repositoryQuery(repositoryId)}`
  );
}

export function getDependencyPath(
  repositoryId: string,
  nodeId: string
): Promise<DependencyPathResponse> {
  return apiRequest<DependencyPathResponse>(
    `/api/v1/analytics/path/${encodeURIComponent(nodeId)}?${repositoryQuery(repositoryId)}`
  );
}

export function getImpactAnalysis(
  repositoryId: string,
  nodeId: string
): Promise<ImpactAnalysisResponse> {
  return apiRequest<ImpactAnalysisResponse>(
    `/api/v1/analytics/impact/${encodeURIComponent(nodeId)}?${repositoryQuery(repositoryId)}`
  );
}

export function getSimilarity(
  repositoryId: string,
  nodeId: string,
  limit = 10
): Promise<SimilarityRanking> {
  const params = new URLSearchParams({ repositoryId, limit: String(limit) });
  return apiRequest<SimilarityRanking>(
    `/api/v1/intelligence/similarity/${encodeURIComponent(nodeId)}?${params}`
  );
}

export function getClusters(
  repositoryId: string,
  nodeType = "function",
  threshold = 0.5
): Promise<ClusterResult> {
  const params = new URLSearchParams({
    repositoryId,
    nodeType,
    threshold: String(threshold)
  });
  return apiRequest<ClusterResult>(`/api/v1/intelligence/clusters?${params}`);
}

export function localizeFailure(payload: JsonPayload): Promise<BugLocalizationResult> {
  return apiRequest<BugLocalizationResult>(
    "/api/v1/intelligence/failures/localize",
    {
      method: "POST",
      body: JSON.stringify(payload)
    },
    "Unable to localize this failure."
  );
}

export function createFailure(repositoryId: string, payload: JsonPayload): Promise<FailureRecord> {
  return apiRequest<FailureRecord>(
    `/api/v1/repositories/${repositoryId}/failures`,
    {
      method: "POST",
      body: JSON.stringify(payload)
    },
    "Unable to save the failure record."
  );
}

export function getFailures(
  repositoryId: string,
  snapshotId?: string
): Promise<FailureCollection> {
  const suffix = snapshotId
    ? `?${new URLSearchParams({ snapshotId }).toString()}`
    : "";
  return apiRequest<FailureCollection>(
    `/api/v1/repositories/${repositoryId}/failures${suffix}`
  );
}

export function confirmFailure(
  failureId: string,
  rootCauseNodeId: string,
  notes: string
): Promise<FailureRecord> {
  return apiRequest<FailureRecord>(
    `/api/v1/failures/${failureId}`,
    {
      method: "PATCH",
      body: JSON.stringify({
        status: "RESOLVED",
        confirmedRootCauseNodeIds: [rootCauseNodeId],
        resolutionNotes: notes,
        resolvedAt: new Date().toISOString()
      })
    },
    "Unable to confirm the root cause."
  );
}

export function queryExplanation(payload: JsonPayload): Promise<ExplanationResponse> {
  return apiRequest<ExplanationResponse>(
    "/api/v1/explanations/query",
    {
      method: "POST",
      body: JSON.stringify(payload)
    },
    "The explanation service is currently unavailable."
  );
}

export async function getRepositoryWorkspace(
  repositoryId: string
): Promise<RepositoryWorkspaceData> {
  const [summary, files, symbols, analysis, graph, critical, components, cycles, topologicalOrder] =
    await Promise.all([
      getRepositorySummary(repositoryId),
      getRepositoryFiles(repositoryId),
      getRepositorySymbols(repositoryId),
      getRepositoryAnalysis(repositoryId),
      getRepositoryGraph(repositoryId),
      getCriticalNodes(repositoryId),
      getComponents(repositoryId),
      getCycles(repositoryId),
      getTopologicalOrder(repositoryId)
    ]);

  const failures = await getFailures(repositoryId, summary.latestSnapshot?.snapshotId);
  return {
    summary,
    files,
    symbols,
    analysis,
    graph,
    critical,
    components,
    cycles,
    topologicalOrder,
    failures
  };
}
