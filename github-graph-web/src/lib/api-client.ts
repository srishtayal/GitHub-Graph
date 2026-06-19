import { env } from "./env";
import type {
  CreateIngestionResponse,
  IngestionJob,
  RepositoryFileList,
  RepositorySummary,
  RepositorySymbolList
} from "./types";

export async function createIngestion(githubUrl: string): Promise<CreateIngestionResponse> {
  const response = await fetch(`${env.apiBaseUrl}/api/v1/repositories/ingestions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ githubUrl })
  });

  if (!response.ok) {
    throw new Error("Unable to create ingestion job");
  }

  return response.json();
}

export async function getIngestionJob(jobId: string): Promise<IngestionJob> {
  const response = await fetch(`${env.apiBaseUrl}/api/v1/ingestion-jobs/${jobId}`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Unable to fetch ingestion job");
  }

  return response.json();
}

export async function getRepositorySummary(repositoryId: string): Promise<RepositorySummary> {
  const response = await fetch(`${env.apiBaseUrl}/api/v1/repositories/${repositoryId}`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Unable to fetch repository summary");
  }

  return response.json();
}

export async function getRepositoryFiles(repositoryId: string): Promise<RepositoryFileList> {
  const response = await fetch(`${env.apiBaseUrl}/api/v1/repositories/${repositoryId}/files`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Unable to fetch repository files");
  }

  return response.json();
}

export async function getRepositorySymbols(repositoryId: string): Promise<RepositorySymbolList> {
  const response = await fetch(`${env.apiBaseUrl}/api/v1/repositories/${repositoryId}/symbols`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Unable to fetch repository symbols");
  }

  return response.json();
}
