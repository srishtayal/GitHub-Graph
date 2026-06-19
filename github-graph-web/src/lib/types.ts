export type CreateIngestionResponse = {
  jobId: string;
  repositoryId: string;
  status: string;
};

export type IngestionJob = {
  jobId: string;
  repositoryId: string | null;
  status: string;
  errorMessage: string | null;
  createdAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
};

export type RepositorySummary = {
  repositoryId: string;
  githubUrl: string;
  owner: string;
  name: string;
  status: string;
  latestSnapshot: {
    snapshotId: string;
    branchName: string;
    commitSha: string;
    totalFiles: number;
    totalDirectories: number;
    languageSummary: Record<string, number>;
  } | null;
};

export type RepositoryFileList = {
  items: Array<{
    fileId: string;
    relativePath: string;
    language: string | null;
    sizeBytes: number;
  }>;
};

export type RepositorySymbolList = {
  items: Array<{
    symbolId: string;
    fileId: string | null;
    symbolType: string;
    name: string;
    qualifiedName: string | null;
    language: string | null;
    startLine: number | null;
    endLine: number | null;
  }>;
};
