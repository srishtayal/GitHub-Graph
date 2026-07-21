import { describe, expect, it } from "vitest";
import {
  buildLayeredProjection,
  formatProjectedEdgeLabel,
  projectionSymbolCount
} from "./graph-projection";
import type { GraphProjectionEdge, GraphProjectionNode } from "./types";

const node = (id: string, incoming = 0, outgoing = 0): GraphProjectionNode => ({
  id,
  displayName: id,
  level: "COMPONENT",
  category: "source-area",
  counts: { files: 1, classes: 2, functions: 3, routes: 1 },
  incomingDependencyCount: incoming,
  outgoingDependencyCount: outgoing,
  criticalityScore: 0,
  childCount: 1,
  representatives: [],
  underlyingNodeIds: [id],
  expandable: true
});

const edge = (source: string, target: string): GraphProjectionEdge => ({
  id: `${source}-${target}`,
  source,
  target,
  type: "AGGREGATED",
  totalRelationshipCount: 6,
  countsByType: { CALLS: 4, IMPORTS: 2 },
  underlyingEdgeIds: ["one", "two"]
});

describe("graph projection helpers", () => {
  it("places dependency targets in a later directed layer", () => {
    const positions = buildLayeredProjection(
      [node("entry", 0, 1), node("service", 1, 1), node("storage", 1, 0)],
      [edge("entry", "service"), edge("service", "storage")]
    );

    expect(positions.get("entry")!.x).toBeLessThan(positions.get("service")!.x);
    expect(positions.get("service")!.x).toBeLessThan(positions.get("storage")!.x);
  });

  it("keeps cyclic and disconnected components in deterministic positions", () => {
    const nodes = [node("alpha"), node("beta"), node("docs")];
    const edges = [edge("alpha", "beta"), edge("beta", "alpha")];

    expect(buildLayeredProjection(nodes, edges)).toEqual(buildLayeredProjection(nodes, edges));
    expect(buildLayeredProjection(nodes, edges).size).toBe(3);
  });

  it("summarizes symbols and the dominant aggregate relationship", () => {
    expect(projectionSymbolCount(node("component"))).toBe(6);
    expect(formatProjectedEdgeLabel(edge("entry", "service"))).toBe("Calls 4 +1");
  });
});
