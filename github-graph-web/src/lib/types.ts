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
  };
  codeFiles: Array<{
    relativePath: string;
    language: string | null;
    classes: string[];
    functions: string[];
    imports: string[];
  }>;
  classes: Array<{
    name: string;
    qualifiedName: string;
    file: string;
    startLine: number;
    bases: string[];
  }>;
  functions: Array<{
    name: string;
    qualifiedName: string;
    file: string;
    startLine: number;
    className: string | null;
    parameters: string[];
    isAsync: boolean;
  }>;
  methodCalls: Array<{
    name: string;
    file: string;
    line: number;
    caller: string | null;
  }>;
  inheritance: Array<{
    className: string;
    baseClass: string;
    file: string;
  }>;
  apiRoutes: Array<{
    method: string;
    path: string;
    handler: string;
    file: string;
    line: number;
  }>;
  moduleDependencies: Array<{
    sourceFile: string;
    targetModule: string;
    targetFile: string | null;
    dependencyType: string;
  }>;
};
