import type {
  GraphProjectionEdge,
  GraphProjectionNode
} from "./types";

export type ProjectionPosition = {
  x: number;
  y: number;
};

const EDGE_TYPE_LABELS: Record<string, string> = {
  CALLS: "Calls",
  IMPORTS: "Imports",
  INHERITS: "Inherits",
  USES: "Uses",
  BELONGS_TO: "Contains"
};

export function projectionSymbolCount(node: GraphProjectionNode): number {
  return node.counts.classes + node.counts.functions + node.counts.routes;
}

export function formatProjectedEdgeLabel(edge: GraphProjectionEdge): string {
  const relationships = Object.entries(edge.countsByType)
    .filter(([, count]) => count > 0)
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]));
  if (relationships.length === 0) {
    return `${edge.totalRelationshipCount} relationships`;
  }
  const [type, count] = relationships[0];
  const remainder = relationships.length > 1 ? ` +${relationships.length - 1}` : "";
  return `${EDGE_TYPE_LABELS[type] ?? humanize(type)} ${count}${remainder}`;
}

export function projectedEdgeBreakdown(edge: GraphProjectionEdge): string[] {
  return Object.entries(edge.countsByType)
    .filter(([, count]) => count > 0)
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
    .map(([type, count]) => `${EDGE_TYPE_LABELS[type] ?? humanize(type)} ${count}`);
}

export function buildLayeredProjection(
  nodes: GraphProjectionNode[],
  edges: GraphProjectionEdge[]
): Map<string, ProjectionPosition> {
  const nodeIds = new Set(nodes.map((node) => node.id));
  const outgoing = new Map<string, Set<string>>();
  const indegree = new Map(nodes.map((node) => [node.id, 0]));
  const connected = new Set<string>();

  for (const edge of edges) {
    if (!nodeIds.has(edge.source) || !nodeIds.has(edge.target) || edge.source === edge.target) {
      continue;
    }
    const targets = outgoing.get(edge.source) ?? new Set<string>();
    if (!targets.has(edge.target)) {
      targets.add(edge.target);
      outgoing.set(edge.source, targets);
      indegree.set(edge.target, (indegree.get(edge.target) ?? 0) + 1);
    }
    connected.add(edge.source);
    connected.add(edge.target);
  }

  const layer = new Map<string, number>();
  const queue = nodes
    .filter((node) => connected.has(node.id) && indegree.get(node.id) === 0)
    .sort(nodeOrder)
    .map((node) => node.id);
  queue.forEach((id) => layer.set(id, 0));

  while (queue.length > 0) {
    const source = queue.shift()!;
    for (const target of Array.from(outgoing.get(source) ?? []).sort()) {
      layer.set(target, Math.max(layer.get(target) ?? 0, (layer.get(source) ?? 0) + 1));
      indegree.set(target, (indegree.get(target) ?? 1) - 1);
      if (indegree.get(target) === 0) {
        queue.push(target);
      }
    }
  }

  const processedLayers = Array.from(layer.values());
  const baseLayerCount = Math.max(3, (processedLayers.length ? Math.max(...processedLayers) : 1) + 1);
  nodes
    .filter((node) => connected.has(node.id) && !layer.has(node.id))
    .sort(nodeOrder)
    .forEach((node, index) => layer.set(node.id, index % baseLayerCount));

  const disconnected = nodes.filter((node) => !connected.has(node.id)).sort(nodeOrder);
  disconnected.forEach((node, index) => layer.set(node.id, index % baseLayerCount));

  const grouped = new Map<number, GraphProjectionNode[]>();
  for (const node of nodes) {
    const nodeLayer = layer.get(node.id) ?? 0;
    grouped.set(nodeLayer, [...(grouped.get(nodeLayer) ?? []), node]);
  }
  grouped.forEach((items) => items.sort(nodeOrder));

  const maxRows = Math.max(1, ...Array.from(grouped.values()).map((items) => items.length));
  const positions = new Map<string, ProjectionPosition>();
  for (const [nodeLayer, items] of grouped) {
    const offset = ((maxRows - items.length) * 158) / 2;
    items.forEach((node, index) => {
      positions.set(node.id, {
        x: nodeLayer * 320,
        y: offset + index * 158
      });
    });
  }
  return positions;
}

function nodeOrder(left: GraphProjectionNode, right: GraphProjectionNode): number {
  const leftDependencies = left.incomingDependencyCount + left.outgoingDependencyCount;
  const rightDependencies = right.incomingDependencyCount + right.outgoingDependencyCount;
  return rightDependencies - leftDependencies || left.displayName.localeCompare(right.displayName);
}

function humanize(value: string): string {
  const normalized = value.toLowerCase().replaceAll("_", " ");
  return normalized.charAt(0).toUpperCase() + normalized.slice(1);
}
