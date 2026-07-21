import { createServer } from "node:http";

const repositoryId = "phase8-repository";
const snapshotId = "phase8-snapshot";
const jobId = "phase8-demo";
const port = Number(process.env.MOCK_API_PORT ?? 8180);

const nodes = [
  node("repo", "repo", "acme/ledger", { owner: "acme", name: "ledger" }),
  node("file-auth", "file", "auth.py", { relativePath: "src/auth.py", language: "Python" }),
  node("file-db", "file", "database.py", { relativePath: "src/database.py", language: "Python" }),
  node("file-api", "file", "routes.py", { relativePath: "src/routes.py", language: "Python" }),
  node("class-user", "class", "User", {
    relativePath: "src/auth.py",
    qualifiedName: "auth.User",
    startLine: 12,
    endLine: 38
  }),
  node("fn-login", "function", "login", {
    relativePath: "src/auth.py",
    qualifiedName: "auth.login",
    startLine: 42,
    endLine: 61,
    parameters: ["email", "password"]
  }),
  node("fn-validate", "function", "validate_credentials", {
    relativePath: "src/auth.py",
    qualifiedName: "auth.validate_credentials",
    startLine: 64,
    endLine: 78
  }),
  node("fn-query", "function", "query_user", {
    relativePath: "src/database.py",
    qualifiedName: "database.query_user",
    startLine: 19,
    endLine: 31
  }),
  node("fn-connect", "function", "db_connection", {
    relativePath: "src/database.py",
    qualifiedName: "database.db_connection",
    startLine: 4,
    endLine: 16
  }),
  node("api-login", "api", "POST /login", {
    relativePath: "src/routes.py",
    path: "/login",
    httpMethod: "POST",
    handler: "login",
    startLine: 14
  }),
  node("module-jwt", "module", "jwt", { moduleName: "jwt", external: true }),
  node("module-db", "module", "database", {
    moduleName: "database",
    relativePath: "src/database.py",
    external: false
  })
];

const edges = [
  edge("e1", "file-auth", "repo", "BELONGS_TO"),
  edge("e2", "file-db", "repo", "BELONGS_TO"),
  edge("e3", "file-api", "repo", "BELONGS_TO"),
  edge("e4", "class-user", "file-auth", "BELONGS_TO"),
  edge("e5", "fn-login", "file-auth", "BELONGS_TO"),
  edge("e6", "fn-validate", "file-auth", "BELONGS_TO"),
  edge("e7", "fn-query", "file-db", "BELONGS_TO"),
  edge("e8", "fn-connect", "file-db", "BELONGS_TO"),
  edge("e9", "api-login", "file-api", "BELONGS_TO"),
  edge("e10", "fn-login", "fn-validate", "CALLS"),
  edge("e11", "fn-validate", "fn-query", "CALLS"),
  edge("e12", "fn-query", "fn-connect", "CALLS"),
  edge("e13", "fn-login", "module-jwt", "USES"),
  edge("e14", "file-auth", "module-db", "IMPORTS"),
  edge("e15", "api-login", "fn-login", "USES"),
  edge("e16", "module-db", "file-db", "USES")
];

const graph = { nodes, edges };
const nodeMap = new Map(nodes.map((item) => [item.id, item]));
let failure = null;

const server = createServer(async (request, response) => {
  if (request.method === "OPTIONS") {
    response.writeHead(204, corsHeaders());
    response.end();
    return;
  }

  const url = new URL(request.url ?? "/", `http://localhost:${port}`);
  const path = url.pathname;
  let payload;

  if (path === `/api/v1/ingestion-jobs/${jobId}`) {
    payload = {
      jobId,
      repositoryId,
      status: "COMPLETED",
      errorMessage: null,
      createdAt: "2026-07-20T10:00:00Z",
      startedAt: "2026-07-20T10:00:01Z",
      finishedAt: "2026-07-20T10:00:08Z"
    };
  } else if (path === `/api/v1/repositories/${repositoryId}`) {
    payload = {
      repositoryId,
      githubUrl: "https://github.com/acme/ledger",
      owner: "acme",
      name: "ledger",
      status: "COMPLETED",
      latestSnapshot: {
        snapshotId,
        branchName: "main",
        commitSha: "8fb23da98b91c30a",
        totalFiles: 14,
        totalDirectories: 5,
        languageSummary: { Python: 10, Markdown: 2, YAML: 1, Dockerfile: 1 }
      }
    };
  } else if (path === `/api/v1/repositories/${repositoryId}/files`) {
    payload = {
      items: nodes
        .filter((item) => item.type === "file")
        .map((item) => ({
          fileId: item.id,
          relativePath: item.properties.relativePath,
          language: "Python",
          sizeBytes: 1480
        }))
    };
  } else if (path === `/api/v1/repositories/${repositoryId}/symbols`) {
    payload = {
      items: nodes
        .filter((item) => ["class", "function"].includes(item.type))
        .map((item) => ({
          symbolId: item.id,
          fileId: null,
          symbolType: item.type,
          name: item.label,
          qualifiedName: item.properties.qualifiedName,
          language: "Python",
          startLine: item.properties.startLine,
          endLine: item.properties.endLine
        }))
    };
  } else if (path === `/api/v1/repositories/${repositoryId}/analysis`) {
    payload = analysisPayload();
  } else if (path === `/api/v1/repositories/${repositoryId}/graph/views/overview`) {
    payload = overviewProjectionPayload();
  } else if (path.startsWith(`/api/v1/repositories/${repositoryId}/graph/views/components/`)) {
    payload = componentProjectionPayload(decodeURIComponent(path.split("/").pop()));
  } else if (path === `/api/v1/repositories/${repositoryId}/graph`) {
    payload = graph;
  } else if (path === "/api/v1/analytics/critical") {
    payload = {
      repositoryId,
      totalReturned: 5,
      nodes: ["fn-login", "fn-validate", "fn-query", "file-auth", "fn-connect"].map(
        (id, index) => ({
          node: nodeMap.get(id),
          inDegree: 4 - Math.min(index, 3),
          outDegree: Math.max(3 - index, 1),
          totalDegree: 7 - index,
          degreeCentrality: 0.64 - index * 0.08
        })
      )
    };
  } else if (path === "/api/v1/analytics/components") {
    payload = {
      repositoryId,
      totalComponents: 2,
      components: [
        { id: "component-auth", size: 9, nodes: nodes.slice(0, 9) },
        { id: "component-api", size: 3, nodes: nodes.slice(9) }
      ]
    };
  } else if (path === "/api/v1/analytics/cycles") {
    payload = {
      repositoryId,
      hasCycles: true,
      totalCycles: 1,
      cycles: [{ nodeIds: ["file-auth", "module-db", "file-db", "file-auth"] }]
    };
  } else if (path === "/api/v1/analytics/topological-order") {
    payload = {
      repositoryId,
      acyclic: false,
      message: "One import cycle detected.",
      order: nodes,
      cycles: [["file-auth", "module-db", "file-db"]]
    };
  } else if (path.startsWith("/api/v1/analytics/path/")) {
    const targetId = decodeURIComponent(path.split("/").pop());
    payload = {
      repositoryId,
      startNodeId: targetId,
      traversalOrder: traversal(targetId, ["fn-validate", "fn-query", "fn-connect", "module-jwt"])
    };
  } else if (path.startsWith("/api/v1/analytics/impact/")) {
    const targetId = decodeURIComponent(path.split("/").pop());
    payload = {
      repositoryId,
      startNodeId: targetId,
      totalAffectedNodes: 4,
      affectedNodes: traversal(targetId, ["fn-query", "fn-validate", "fn-login", "api-login"])
    };
  } else if (path.startsWith("/api/v1/intelligence/similarity/")) {
    const targetId = decodeURIComponent(path.split("/").pop());
    payload = similarityPayload(targetId);
  } else if (path === "/api/v1/intelligence/clusters") {
    payload = {
      nodeType: "function",
      threshold: Number(url.searchParams.get("threshold") ?? 0.5),
      clusters: [
        {
          clusterId: "cluster-auth",
          nodeType: "function",
          threshold: 0.5,
          memberNodeIds: ["fn-login", "fn-validate", "fn-query"],
          links: [
            { sourceNodeId: "fn-login", targetNodeId: "fn-validate", score: 0.78 },
            { sourceNodeId: "fn-validate", targetNodeId: "fn-query", score: 0.67 }
          ]
        },
        {
          clusterId: "cluster-data",
          nodeType: "function",
          threshold: 0.5,
          memberNodeIds: ["fn-query", "fn-connect"],
          links: [{ sourceNodeId: "fn-query", targetNodeId: "fn-connect", score: 0.81 }]
        }
      ]
    };
  } else if (path === "/api/v1/intelligence/failures/localize") {
    payload = localizationPayload();
  } else if (
    path === `/api/v1/repositories/${repositoryId}/failures` &&
    request.method === "POST"
  ) {
    failure = failurePayload(localizationPayload());
    payload = failure;
  } else if (path === `/api/v1/repositories/${repositoryId}/failures`) {
    payload = { repositoryId, snapshotId, failures: failure ? [failure] : [] };
  } else if (path.startsWith("/api/v1/failures/") && request.method === "PATCH") {
    const body = await bodyJson(request);
    failure = {
      ...(failure ?? failurePayload(localizationPayload())),
      status: "RESOLVED",
      confirmedRootCauseNodeIds: body.confirmedRootCauseNodeIds ?? ["fn-connect"],
      resolutionNotes: body.resolutionNotes,
      resolvedAt: body.resolvedAt,
      updatedAt: body.resolvedAt
    };
    payload = failure;
  } else if (path === "/api/v1/explanations/query") {
    const body = await bodyJson(request);
    payload = explanationPayload(body);
  } else {
    response.writeHead(404, corsHeaders());
    response.end(JSON.stringify({ message: `No fixture for ${request.method} ${path}` }));
    return;
  }

  response.writeHead(200, { ...corsHeaders(), "Content-Type": "application/json" });
  response.end(JSON.stringify(payload));
});

server.listen(port, () => {
  console.log(`Phase 8 contract API listening on http://127.0.0.1:${port} (${jobId})`);
});

function node(id, type, label, properties) {
  return { id, type, label, properties };
}

function edge(id, source, target, type) {
  return { id, source, target, type, properties: {} };
}

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "http://127.0.0.1:4173",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Allow-Methods": "GET,POST,PATCH,OPTIONS"
  };
}

function bodyJson(request) {
  return new Promise((resolve) => {
    let body = "";
    request.on("data", (chunk) => {
      body += chunk;
    });
    request.on("end", () => resolve(body ? JSON.parse(body) : {}));
  });
}

function traversal(targetId, relatedIds) {
  return [targetId, ...relatedIds]
    .filter((id, index, all) => nodeMap.has(id) && all.indexOf(id) === index)
    .map((id, index) => ({
      node: nodeMap.get(id),
      depth: Math.min(index, 3),
      predecessorNodeId: index ? targetId : null,
      viaEdgeType: index ? (index % 2 ? "CALLS" : "USES") : null
    }));
}

function analysisPayload() {
  return {
    ingestionJobId: jobId,
    status: "COMPLETED",
    summary: {
      totalFiles: 14,
      totalDirectories: 5,
      languageSummary: { Python: 10, Markdown: 2, YAML: 1, Dockerfile: 1 },
      totalClasses: 4,
      totalFunctions: 18,
      totalMethodCalls: 31,
      totalApiRoutes: 5,
      totalModuleDependencies: 9,
      totalGraphNodes: nodes.length,
      totalGraphEdges: edges.length
    },
    codeFiles: [
      {
        relativePath: "src/auth.py",
        language: "Python",
        classes: ["User"],
        functions: ["login", "validate_credentials"],
        imports: ["jwt", "database"]
      }
    ],
    classes: [],
    functions: [],
    methodCalls: [],
    inheritance: [],
    apiRoutes: [],
    moduleDependencies: []
  };
}

function overviewProjectionPayload() {
  const components = [
    projectionNode("component-routes", "HTTP Interface", "source-area", 1, 1, 2, 0.25, ["file-api"]),
    projectionNode("component-auth", "Authentication", "source-area", 1, 3, 4, 0.58, ["file-auth", "fn-login"]),
    projectionNode("component-data", "Data Access", "source-area", 1, 2, 3, 0.5, ["file-db", "fn-connect"]),
    projectionNode("component-external", "External Dependencies", "external-dependencies", 0, 0, 2, 0.17, ["module-jwt", "module-db"])
  ];
  return projectionPayload("OVERVIEW", "repo", components, [
    projectionEdge("projection-edge-routes-auth", "component-routes", "component-auth", { USES: 1 }),
    projectionEdge("projection-edge-auth-data", "component-auth", "component-data", { CALLS: 2, IMPORTS: 1 }),
    projectionEdge("projection-edge-auth-external", "component-auth", "component-external", { IMPORTS: 1, USES: 1 })
  ]);
}

function componentProjectionPayload(componentId) {
  const componentFiles = {
    "component-routes": ["file-api"],
    "component-auth": ["file-auth", "file-db", "module-jwt"],
    "component-data": ["file-db", "module-db"],
    "component-external": ["module-jwt", "module-db"]
  }[componentId] ?? ["file-auth"];
  const projectedNodes = componentFiles.map((id, index) => {
    const raw = nodeMap.get(id);
    return projectionNode(
      id,
      raw?.label ?? id,
      raw?.type ?? "file",
      raw?.type === "file" ? 1 : 0,
      raw?.type === "file" ? 3 : 0,
      index ? 1 : 2,
      index ? 0.2 : 0.4,
      [id]
    );
  });
  const projectedEdges = projectedNodes.length > 1
    ? projectedNodes.slice(1).map((item, index) =>
        projectionEdge(`component-edge-${index}`, projectedNodes[0].id, item.id, { IMPORTS: 1 })
      )
    : [];
  return projectionPayload("COMPONENT", componentId, projectedNodes, projectedEdges);
}

function projectionPayload(level, rootId, projectedNodes, projectedEdges) {
  return {
    repositoryId,
    snapshotId,
    level,
    rootId,
    suggestedMaximumNodes: level === "OVERVIEW" ? 15 : 40,
    truncated: false,
    totals: {
      rawNodeCount: nodes.length,
      rawEdgeCount: edges.length,
      projectedNodeCount: projectedNodes.length,
      projectedEdgeCount: projectedEdges.length
    },
    nodes: projectedNodes,
    edges: projectedEdges
  };
}

function projectionNode(id, displayName, category, files, symbols, dependencies, criticalityScore, representatives) {
  return {
    id,
    displayName,
    level: category === "file" || category === "module" ? category.toUpperCase() : "COMPONENT",
    category,
    counts: { files, classes: 0, functions: symbols, routes: 0 },
    incomingDependencyCount: Math.floor(dependencies / 2),
    outgoingDependencyCount: Math.ceil(dependencies / 2),
    criticalityScore,
    childCount: Math.max(files, 1),
    representatives: representatives.map((referenceId) => ({
      id: referenceId,
      displayName: nodeMap.get(referenceId)?.label ?? referenceId,
      type: nodeMap.get(referenceId)?.type ?? "file"
    })),
    underlyingNodeIds: representatives,
    expandable: true
  };
}

function projectionEdge(id, source, target, countsByType) {
  const totalRelationshipCount = Object.values(countsByType).reduce((sum, count) => sum + count, 0);
  return {
    id,
    source,
    target,
    type: "AGGREGATED",
    totalRelationshipCount,
    countsByType,
    underlyingEdgeIds: Array.from({ length: totalRelationshipCount }, (_, index) => `${id}-raw-${index}`)
  };
}

function similarityPayload(targetId) {
  const candidateIds = ["fn-validate", "fn-query", "fn-connect"].filter((id) => id !== targetId);
  return {
    targetNodeId: targetId,
    nodeType: "function",
    results: candidateIds.map((candidateNodeId, index) => ({
      targetNodeId: targetId,
      candidateNodeId,
      nodeType: "function",
      score: 0.84 - index * 0.13,
      featureScores: {
        calledNodes: { score: 0.9 - index * 0.1, matchedFeatures: ["fn-query"] },
        neighborNodes: { score: 0.76 - index * 0.09, matchedFeatures: ["file-auth"] },
        callerNodes: { score: 0.62 - index * 0.08, matchedFeatures: ["api-login"] }
      },
      clusterId: index < 2 ? "cluster-auth" : "cluster-data"
    }))
  };
}

function localizationPayload() {
  return {
    resolvedFailurePath: {
      nodeIds: ["api-login", "fn-login", "fn-query", "fn-connect"],
      stackFrameNodeIds: ["fn-login", "fn-query", "fn-connect"],
      errorSignature: {
        exceptionType: "ConnectionError",
        messageFingerprint: "connection-pool-exhausted"
      },
      unresolvedReferences: [
        { kind: "stackFrame", value: "framework.dispatch", detail: "External framework frame" }
      ]
    },
    impactedNodeIds: ["fn-query", "fn-validate", "fn-login", "api-login"],
    similarPastFailures: [],
    suspectedRootCauses: [
      {
        nodeId: "fn-connect",
        score: 0.91,
        confidence: "high",
        reasons: [
          { kind: "stack_evidence", weight: 0.3, detail: "Deepest resolved stack frame" },
          { kind: "path_evidence", weight: 0.35, detail: "Present in resolved failure path" },
          { kind: "criticality", weight: 0.05, detail: "Connected database function" }
        ]
      },
      {
        nodeId: "fn-query",
        score: 0.72,
        confidence: "medium",
        reasons: [
          { kind: "stack_evidence", weight: 0.3, detail: "Resolved stack frame" },
          { kind: "structural_evidence", weight: 0.1, detail: "Direct database dependency" }
        ]
      }
    ],
    reasoningMetadata: { algorithmVersion: "phase6-v1" }
  };
}

function failurePayload(localization) {
  const now = new Date().toISOString();
  return {
    failureId: "failure-phase8",
    repositoryId,
    snapshotId,
    status: "OPEN",
    failingNodeId: "fn-login",
    errorLog: "ConnectionError: connection pool exhausted",
    stackTrace: "File src/database.py, line 8, in db_connection",
    errorSignature: localization.resolvedFailurePath.errorSignature,
    resolvedFailurePathNodeIds: localization.resolvedFailurePath.nodeIds,
    confirmedRootCauseNodeIds: [],
    resolutionNotes: null,
    occurredAt: now,
    resolvedAt: null,
    createdAt: now,
    updatedAt: now,
    localization
  };
}

function explanationPayload(body) {
  const targetId = body.targetNodeId ?? "fn-login";
  const nodeLabel = nodeMap.get(targetId)?.label ?? targetId;
  return {
    intent: body.query.toLowerCase().includes("breaks") ? "impact_analysis" : "dependency_flow",
    answer:
      `${nodeLabel} depends on credential validation, user lookup, and the database connection path. ` +
      "The graph shows that a database connection failure can propagate through query_user to login and the POST /login route. " +
      "This is a graph-backed dependency explanation, not a claim about runtime state.",
    supportingEvidence: [
      {
        evidenceId: "analytics:dependency-trace",
        sourceType: "dependencyTrace",
        rationale: "The depth-first trace connects the selected function to its callees."
      },
      {
        evidenceId: "graph:referenced-nodes",
        sourceType: "graph",
        rationale: "The referenced functions and API route exist in this repository snapshot."
      }
    ],
    referencedNodeIds: [targetId, "fn-validate", "fn-query", "fn-connect", "api-login"],
    referencedEdgeIds: ["e10", "e11", "e12", "e15"],
    confidence: "high",
    limitations: ["Static analysis does not include production runtime values."],
    followUpSuggestions: [
      "What breaks if db_connection fails?",
      "Which function is most critical in this flow?"
    ],
    snapshotMetadata: {
      repositoryId,
      snapshotId,
      branchName: "main",
      commitSha: "8fb23da98b91c30a"
    },
    modelMetadata: {
      provider: "gemini",
      model: "gemini-3.1-flash-lite",
      promptVersion: "phase7-v1",
      orchestrationVersion: "grounded-query-v1"
    }
  };
}
