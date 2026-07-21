import { describe, expect, it } from "vitest";
import {
  DEFAULT_EDGE_TYPES,
  defaultEnabledEdgeTypes,
  selectVisibleGraph
} from "./graph-visibility";
import type { GraphEdge, GraphNode, RepositoryGraph } from "./types";

const nodes: GraphNode[] = [
  node("repo", "repo"),
  node("file:a", "file"),
  node("file:b", "file"),
  node("function:a", "function"),
  node("function:b", "function"),
  node("isolated", "class")
];

const edges: GraphEdge[] = [
  edge("belongs", "file:a", "repo", "BELONGS_TO"),
  edge("imports", "file:a", "file:b", "IMPORTS"),
  edge("calls", "function:a", "function:b", "CALLS"),
  edge("inherits", "function:b", "file:b", "INHERITS"),
  edge("uses", "function:a", "file:a", "USES")
];

const graph: RepositoryGraph = { nodes, edges };
const allNodeTypes = new Set(nodes.map((item) => item.type));

describe("defaultEnabledEdgeTypes", () => {
  it("enables useful relationships and hides containment on first load", () => {
    const enabled = defaultEnabledEdgeTypes(edges.map((item) => item.type));

    expect(Array.from(enabled)).toEqual(DEFAULT_EDGE_TYPES);
    expect(enabled.has("BELONGS_TO")).toBe(false);
    expect(enabled.has("USES")).toBe(false);
  });

  it("returns the same useful defaults after a reload", () => {
    const available = edges.map((item) => item.type);

    expect(defaultEnabledEdgeTypes(available)).toEqual(defaultEnabledEdgeTypes(available));
    expect(defaultEnabledEdgeTypes(available).size).toBe(3);
  });
});

describe("selectVisibleGraph", () => {
  it("renders every enabled edge type for a complete small graph", () => {
    const visible = selectVisibleGraph({
      graph,
      enabledNodeTypes: allNodeTypes,
      enabledEdgeTypes: new Set(edges.map((item) => item.type))
    });

    expect(visible.edges.map((item) => item.id)).toEqual(edges.map((item) => item.id));
  });

  it("removes only the disabled relationship type", () => {
    const visible = selectVisibleGraph({
      graph,
      enabledNodeTypes: allNodeTypes,
      enabledEdgeTypes: new Set(["IMPORTS", "INHERITS"])
    });

    expect(visible.edges.map((item) => item.type)).toEqual(["IMPORTS", "INHERITS"]);
  });

  it("never renders an edge without both endpoints", () => {
    const visible = selectVisibleGraph({
      graph,
      enabledNodeTypes: new Set(["function"]),
      enabledEdgeTypes: new Set(["CALLS", "USES"])
    });

    expect(visible.edges).toHaveLength(1);
    expect(visible.nodeIds.has(visible.edges[0].source)).toBe(true);
    expect(visible.nodeIds.has(visible.edges[0].target)).toBe(true);
  });

  it("includes every direct endpoint when a node is selected", () => {
    const visible = selectVisibleGraph({
      graph,
      enabledNodeTypes: allNodeTypes,
      enabledEdgeTypes: new Set(["CALLS", "USES"]),
      selectedNodeId: "function:a",
      maxVisibleNodes: 2
    });

    expect(Array.from(visible.nodeIds).sort()).toEqual(
      ["file:a", "function:a", "function:b"].sort()
    );
    expect(visible.nodeIds.has("isolated")).toBe(false);
    expect(visible.edges.map((item) => item.type).sort()).toEqual(["CALLS", "USES"]);
  });

  it("prioritizes repository and critical connected nodes instead of slicing", () => {
    const visible = selectVisibleGraph({
      graph,
      enabledNodeTypes: allNodeTypes,
      enabledEdgeTypes: new Set(["IMPORTS", "INHERITS"]),
      criticalNodeIds: ["file:b"],
      maxVisibleNodes: 3
    });

    expect(visible.nodeIds.has("repo")).toBe(true);
    expect(visible.nodeIds.has("file:b")).toBe(true);
    expect(visible.nodeIds.has("isolated")).toBe(false);
    visible.edges.forEach((item) => {
      expect(visible.nodeIds.has(item.source)).toBe(true);
      expect(visible.nodeIds.has(item.target)).toBe(true);
    });
  });
});

function node(id: string, type: string): GraphNode {
  return { id, type, label: id, properties: {} };
}

function edge(id: string, source: string, target: string, type: string): GraphEdge {
  return { id, source, target, type, properties: {} };
}
