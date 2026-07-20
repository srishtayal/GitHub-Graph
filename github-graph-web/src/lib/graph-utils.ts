import type { GraphEdge, GraphNode, RepositoryGraph } from "./types";

export const NODE_TYPE_META: Record<
  string,
  { label: string; color: string; short: string }
> = {
  repo: { label: "Repository", color: "#ff6b4a", short: "R" },
  file: { label: "File", color: "#2a9d8f", short: "F" },
  class: { label: "Class", color: "#e9c46a", short: "C" },
  function: { label: "Function", color: "#4e7cff", short: "Fn" },
  api: { label: "API route", color: "#e76f91", short: "API" },
  module: { label: "Module", color: "#8ab17d", short: "M" }
};

export const EDGE_TYPE_META: Record<string, { label: string; color: string }> = {
  BELONGS_TO: { label: "Belongs to", color: "#8b96a8" },
  IMPORTS: { label: "Imports", color: "#2a9d8f" },
  CALLS: { label: "Calls", color: "#4e7cff" },
  USES: { label: "Uses", color: "#e76f91" },
  INHERITS: { label: "Inherits", color: "#e9a23b" }
};

export function nodeMeta(type: string) {
  return NODE_TYPE_META[type] ?? {
    label: type,
    color: "#7f8a9b",
    short: type.slice(0, 2).toUpperCase()
  };
}

export function edgeMeta(type: string) {
  return EDGE_TYPE_META[type] ?? { label: type, color: "#8b96a8" };
}

export function nodePath(node: GraphNode): string | null {
  const value = node.properties.relativePath;
  return typeof value === "string" ? value : null;
}

export function nodeQualifiedName(node: GraphNode): string {
  const qualified = node.properties.qualifiedName;
  return typeof qualified === "string" ? qualified : node.label;
}

export function propertyText(value: unknown): string {
  if (Array.isArray(value)) {
    return value.join(", ");
  }
  if (value === null || value === undefined) {
    return "—";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

export function graphIndex(graph: RepositoryGraph): {
  nodes: Map<string, GraphNode>;
  incoming: Map<string, GraphEdge[]>;
  outgoing: Map<string, GraphEdge[]>;
} {
  const nodes = new Map(graph.nodes.map((node) => [node.id, node]));
  const incoming = new Map<string, GraphEdge[]>();
  const outgoing = new Map<string, GraphEdge[]>();

  for (const edge of graph.edges) {
    incoming.set(edge.target, [...(incoming.get(edge.target) ?? []), edge]);
    outgoing.set(edge.source, [...(outgoing.get(edge.source) ?? []), edge]);
  }

  return { nodes, incoming, outgoing };
}

export function graphNeighborhood(
  graph: RepositoryGraph,
  centerId: string,
  depth = 1
): Set<string> {
  const selected = new Set([centerId]);
  let frontier = new Set([centerId]);

  for (let level = 0; level < depth; level += 1) {
    const next = new Set<string>();
    for (const edge of graph.edges) {
      if (frontier.has(edge.source)) {
        selected.add(edge.target);
        next.add(edge.target);
      }
      if (frontier.has(edge.target)) {
        selected.add(edge.source);
        next.add(edge.source);
      }
    }
    frontier = next;
  }

  return selected;
}

export function formatPercent(value: number): string {
  return `${Math.round(value * 100)}%`;
}

export function compactNumber(value: number): string {
  return new Intl.NumberFormat("en", { notation: "compact" }).format(value);
}

export function formatBytes(value: number): string {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}
