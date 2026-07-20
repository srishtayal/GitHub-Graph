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
  errorCategory?: string | null;
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

export type RepositoryGraph = {
  nodes: Array<{
    id: string;
    type: string;
    label: string;
    properties: Record<string, unknown>;
  }>;
  edges: Array<{
    id: string;
    source: string;
    target: string;
    type: string;
    properties: Record<string, unknown>;
  }>;
};
