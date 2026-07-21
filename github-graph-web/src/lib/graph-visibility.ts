import type { GraphEdge, RepositoryGraph } from "./types";

export const DEFAULT_EDGE_TYPES = ["IMPORTS", "CALLS", "INHERITS"] as const;

type VisibilityOptions = {
  graph: RepositoryGraph;
  enabledNodeTypes: Set<string>;
  enabledEdgeTypes: Set<string>;
  criticalNodeIds?: string[];
  selectedNodeId?: string | null;
  focusMode?: boolean;
  maxVisibleNodes?: number;
};

type VisibleGraph = {
  nodeIds: Set<string>;
  edges: GraphEdge[];
};

export function defaultEnabledEdgeTypes(edgeTypes: Iterable<string>): Set<string> {
  const available = new Set(edgeTypes);
  return new Set(DEFAULT_EDGE_TYPES.filter((type) => available.has(type)));
}

export function selectVisibleGraph({
  graph,
  enabledNodeTypes,
  enabledEdgeTypes,
  criticalNodeIds = [],
  selectedNodeId = null,
  focusMode = false,
  maxVisibleNodes = 180
}: VisibilityOptions): VisibleGraph {
  const eligibleNodes = graph.nodes.filter((node) => enabledNodeTypes.has(node.type));
  const eligibleIds = new Set(eligibleNodes.map((node) => node.id));
  const eligibleEdges = graph.edges.filter(
    (edge) =>
      enabledEdgeTypes.has(edge.type) &&
      eligibleIds.has(edge.source) &&
      eligibleIds.has(edge.target)
  );

  if (!focusMode && eligibleNodes.length <= maxVisibleNodes) {
    return {
      nodeIds: eligibleIds,
      edges: eligibleEdges
    };
  }

  const adjacency = buildAdjacency(eligibleEdges);
  const nodeTypesById = new Map(eligibleNodes.map((node) => [node.id, node.type]));
  const criticalRanks = new Map(criticalNodeIds.map((id, index) => [id, index]));
  const compareIds = (left: string, right: string) =>
    priority(right, nodeTypesById, adjacency, criticalRanks) -
      priority(left, nodeTypesById, adjacency, criticalRanks) || left.localeCompare(right);
  const selected = new Set<string>();

  if (selectedNodeId && eligibleIds.has(selectedNodeId)) {
    selected.add(selectedNodeId);

    // A selected node must never appear without the endpoints of its visible relationships.
    const directNeighbors = Array.from(adjacency.get(selectedNodeId) ?? []).sort(compareIds);
    directNeighbors.forEach((id) => selected.add(id));

    if (focusMode) {
      expandBreadthFirst(selectedNodeId, 2, selected, adjacency, compareIds, maxVisibleNodes);
    }
  } else {
    const seeds = eligibleNodes
      .map((node) => node.id)
      .filter(
        (id) =>
          nodeTypesById.get(id) === "repo" ||
          criticalRanks.has(id) ||
          (adjacency.get(id)?.size ?? 0) > 0
      )
      .sort(compareIds);

    for (const seed of seeds) {
      if (selected.size >= maxVisibleNodes) break;
      if (selected.has(seed)) continue;
      expandBreadthFirst(seed, Number.POSITIVE_INFINITY, selected, adjacency, compareIds, maxVisibleNodes);
    }
  }

  const visibleEdges = eligibleEdges.filter(
    (edge) => selected.has(edge.source) && selected.has(edge.target)
  );
  return { nodeIds: selected, edges: visibleEdges };
}

function buildAdjacency(edges: GraphEdge[]): Map<string, Set<string>> {
  const adjacency = new Map<string, Set<string>>();
  for (const edge of edges) {
    adjacency.set(edge.source, new Set([...(adjacency.get(edge.source) ?? []), edge.target]));
    adjacency.set(edge.target, new Set([...(adjacency.get(edge.target) ?? []), edge.source]));
  }
  return adjacency;
}

function expandBreadthFirst(
  seed: string,
  maxDepth: number,
  selected: Set<string>,
  adjacency: Map<string, Set<string>>,
  compareIds: (left: string, right: string) => number,
  limit: number
) {
  const visited = new Set<string>();
  const queue: Array<{ id: string; depth: number }> = [{ id: seed, depth: 0 }];

  while (queue.length > 0 && selected.size < limit) {
    const current = queue.shift();
    if (!current || visited.has(current.id)) continue;
    visited.add(current.id);
    selected.add(current.id);
    if (current.depth >= maxDepth) continue;

    const neighbors = Array.from(adjacency.get(current.id) ?? []).sort(compareIds);
    for (const neighbor of neighbors) {
      if (!visited.has(neighbor)) queue.push({ id: neighbor, depth: current.depth + 1 });
    }
  }
}

function priority(
  id: string,
  nodeTypesById: Map<string, string>,
  adjacency: Map<string, Set<string>>,
  criticalRanks: Map<string, number>
): number {
  const typeScore = nodeTypesById.get(id) === "repo" ? 1_000_000 : 0;
  const criticalRank = criticalRanks.get(id);
  const criticalScore = criticalRank === undefined ? 0 : 100_000 - criticalRank;
  return typeScore + criticalScore + (adjacency.get(id)?.size ?? 0) * 100;
}
