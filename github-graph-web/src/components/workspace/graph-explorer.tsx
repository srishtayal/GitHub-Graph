"use client";

import { useDeferredValue, useEffect, useMemo, useState } from "react";
import {
  Background,
  BackgroundVariant,
  Controls,
  MarkerType,
  MiniMap,
  Position,
  ReactFlow,
  type Edge,
  type Node
} from "@xyflow/react";
import {
  Filter,
  Focus,
  Layers3,
  Search,
  SlidersHorizontal,
  X
} from "lucide-react";
import { getDependencyPath, getImpactAnalysis } from "@/lib/api-client";
import {
  edgeMeta,
  nodeMeta,
  nodePath,
  nodeQualifiedName
} from "@/lib/graph-utils";
import {
  defaultEnabledEdgeTypes,
  selectVisibleGraph
} from "@/lib/graph-visibility";
import type {
  CriticalNodesResponse,
  GraphNode,
  RepositoryGraph
} from "@/lib/types";
import { NodeDetailPanel } from "./node-detail-panel";

const MAX_VISIBLE_NODES = 180;

type GraphExplorerProps = {
  repositoryId: string;
  graph: RepositoryGraph;
  critical: CriticalNodesResponse;
  selectedNode: GraphNode | null;
  onSelectNode: (node: GraphNode | null) => void;
};

export function GraphExplorer({
  repositoryId,
  graph,
  critical,
  selectedNode,
  onSelectNode
}: GraphExplorerProps) {
  const nodeTypes = useMemo(
    () => Array.from(new Set(graph.nodes.map((node) => node.type))).sort(),
    [graph.nodes]
  );
  const edgeTypes = useMemo(
    () => Array.from(new Set(graph.edges.map((edge) => edge.type))).sort(),
    [graph.edges]
  );
  const [enabledNodeTypes, setEnabledNodeTypes] = useState(() => new Set(nodeTypes));
  const [enabledEdgeTypes, setEnabledEdgeTypes] = useState(() => defaultEnabledEdgeTypes(edgeTypes));
  const [query, setQuery] = useState("");
  const deferredQuery = useDeferredValue(query);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [focusMode, setFocusMode] = useState(false);
  const [highlightedIds, setHighlightedIds] = useState<Set<string>>(new Set());
  const [highlightLabel, setHighlightLabel] = useState<string | null>(null);
  const [busyAction, setBusyAction] = useState<"trace" | "impact" | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    setEnabledNodeTypes(new Set(nodeTypes));
    setEnabledEdgeTypes(defaultEnabledEdgeTypes(edgeTypes));
  }, [edgeTypes, nodeTypes, repositoryId]);

  const searchResults = useMemo(() => {
    const normalized = deferredQuery.trim().toLowerCase();
    if (!normalized) return [];
    return graph.nodes
      .filter((node) =>
        [node.label, nodeQualifiedName(node), nodePath(node) ?? "", node.type]
          .join(" ")
          .toLowerCase()
          .includes(normalized)
      )
      .slice(0, 9);
  }, [deferredQuery, graph.nodes]);

  const visibleGraph = useMemo(
    () =>
      selectVisibleGraph({
        graph,
        enabledNodeTypes,
        enabledEdgeTypes,
        criticalNodeIds: critical.nodes.map((item) => item.node.id),
        selectedNodeId: selectedNode?.id,
        focusMode,
        maxVisibleNodes: MAX_VISIBLE_NODES
      }),
    [critical.nodes, enabledEdgeTypes, enabledNodeTypes, focusMode, graph, selectedNode?.id]
  );
  const visibleNodeIds = visibleGraph.nodeIds;

  const flowNodes = useMemo<Node[]>(() => {
    const visible = graph.nodes.filter((node) => visibleNodeIds.has(node.id));
    const makeNode = (node: GraphNode, position: { x: number; y: number }): Node => {
      const isSelected = selectedNode?.id === node.id;
      const isHighlighted = highlightedIds.has(node.id);
      const meta = nodeMeta(node.type);
      return {
        id: node.id,
        position,
        data: { label: node.label },
        className: `flow-node flow-node-${node.type}${isSelected ? " is-selected" : ""}${isHighlighted ? " is-highlighted" : ""}`,
        style: {
          "--node-color": meta.color
        } as React.CSSProperties,
        sourcePosition: Position.Right,
        targetPosition: Position.Left
      };
    };

    if (visible.length <= 40) {
      const center = visible.find((node) => node.type === "repo") ?? visible[0];
      const orbit = visible.filter((node) => node.id !== center?.id);
      const radial = orbit.map((node, index) => {
        const angle = (index / Math.max(orbit.length, 1)) * Math.PI * 2 - Math.PI / 2;
        return makeNode(node, {
          x: 340 + Math.cos(angle) * 300,
          y: 190 + Math.sin(angle) * 135
        });
      });
      return center ? [makeNode(center, { x: 340, y: 190 }), ...radial] : radial;
    }

    const grouped = new Map<string, GraphNode[]>();
    for (const node of visible) {
      grouped.set(node.type, [...(grouped.get(node.type) ?? []), node]);
    }
    return Array.from(grouped.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .flatMap(([, nodes], column) =>
        nodes.map((node, index) =>
          makeNode(node, {
            x: column * 265 + (index % 2) * 26,
            y: index * 74 + (column % 2) * 32
          })
        )
      );
  }, [graph.nodes, highlightedIds, selectedNode, visibleNodeIds]);

  const flowEdges = useMemo<Edge[]>(
    () =>
      visibleGraph.edges
        .map((edge) => {
          const highlighted =
            highlightedIds.has(edge.source) && highlightedIds.has(edge.target);
          return {
            id: edge.id,
            source: edge.source,
            target: edge.target,
            label: highlighted ? edge.type : undefined,
            type: "smoothstep",
            animated: highlighted,
            markerEnd: {
              type: MarkerType.ArrowClosed,
              width: 13,
              height: 13,
              color: edgeMeta(edge.type).color
            },
            style: {
              stroke: highlighted ? "#ff6b4a" : edgeMeta(edge.type).color,
              strokeWidth: highlighted ? 2.4 : 1,
              opacity: highlighted ? 1 : 0.42
            },
            labelStyle: { fill: "#101828", fontSize: 10, fontWeight: 700 },
            labelBgStyle: { fill: "#fffdf7", fillOpacity: 0.9 }
          };
        }),
    [highlightedIds, visibleGraph.edges]
  );

  async function runAnalysis(mode: "trace" | "impact") {
    if (!selectedNode) return;
    setBusyAction(mode);
    setActionError(null);
    try {
      if (mode === "trace") {
        const result = await getDependencyPath(repositoryId, selectedNode.id);
        setHighlightedIds(new Set(result.traversalOrder.map((item) => item.node.id)));
        setHighlightLabel(`${result.traversalOrder.length} dependency nodes traced`);
      } else {
        const result = await getImpactAnalysis(repositoryId, selectedNode.id);
        setHighlightedIds(new Set(result.affectedNodes.map((item) => item.node.id)));
        setHighlightLabel(`${result.totalAffectedNodes} downstream nodes affected`);
      }
      setFocusMode(true);
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "Analysis failed.");
    } finally {
      setBusyAction(null);
    }
  }

  function selectFromSearch(node: GraphNode) {
    onSelectNode(node);
    setQuery("");
    setFocusMode(true);
  }

  function toggleNodeType(type: string) {
    setEnabledNodeTypes((current) => {
      const next = new Set(current);
      if (next.has(type)) next.delete(type);
      else next.add(type);
      return next;
    });
  }

  function toggleEdgeType(type: string) {
    setEnabledEdgeTypes((current) => {
      const next = new Set(current);
      if (next.has(type)) next.delete(type);
      else next.add(type);
      return next;
    });
  }

  function selectAllFilters() {
    setEnabledNodeTypes(new Set(nodeTypes));
    setEnabledEdgeTypes(new Set(edgeTypes));
  }

  function clearAllFilters() {
    setEnabledNodeTypes(new Set());
    setEnabledEdgeTypes(new Set());
  }

  function resetFilters() {
    setEnabledNodeTypes(new Set(nodeTypes));
    setEnabledEdgeTypes(defaultEnabledEdgeTypes(edgeTypes));
  }

  const edgeGuidance =
    flowNodes.length === 0
      ? "No node types are selected. Reset filters or select the node types you want to inspect."
      : graph.edges.length === 0
        ? "No relationships were extracted for this repository snapshot."
        : enabledEdgeTypes.size === 0
          ? "No edge types are selected. Enable Imports, Calls, Inherits, Uses, or Belongs to."
          : flowEdges.length === 0
            ? "No enabled relationships connect the visible nodes. Select a node, widen the node filters, or reset filters."
            : null;

  const eligibleNodeCount = graph.nodes.filter((node) => enabledNodeTypes.has(node.type)).length;
  const degraded =
    eligibleNodeCount > visibleNodeIds.size &&
    (eligibleNodeCount > MAX_VISIBLE_NODES || focusMode);

  return (
    <div className="workspace-view graph-view animate-in">
      <div className="view-heading graph-heading">
        <div>
          <p className="section-kicker"><Layers3 size={14} /> Interactive code graph</p>
          <h2>Explore structure and behavior.</h2>
          <p>Search a symbol, inspect its relationships, then trace dependencies or impact.</p>
        </div>
        <div className="graph-toolbar">
          <div className="graph-search">
            <Search size={17} />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Find file, function, class or route…"
              aria-label="Search graph nodes"
            />
            {query ? (
              <button onClick={() => setQuery("")} aria-label="Clear search"><X size={15} /></button>
            ) : null}
            {searchResults.length > 0 ? (
              <div className="search-results">
                {searchResults.map((node) => (
                  <button key={node.id} onClick={() => selectFromSearch(node)}>
                    <span style={{ background: nodeMeta(node.type).color }}>
                      {nodeMeta(node.type).short}
                    </span>
                    <span><strong>{node.label}</strong><small>{nodePath(node) ?? node.type}</small></span>
                  </button>
                ))}
              </div>
            ) : null}
          </div>
          <button
            className={`toolbar-button ${focusMode ? "is-active" : ""}`}
            onClick={() => setFocusMode((value) => !value)}
            disabled={!selectedNode}
          >
            <Focus size={16} /> Neighborhood
          </button>
          <button
            className={`toolbar-button ${filtersOpen ? "is-active" : ""}`}
            onClick={() => setFiltersOpen((value) => !value)}
          >
            <SlidersHorizontal size={16} /> Filters
          </button>
        </div>
      </div>

      {filtersOpen ? (
        <div className="filter-drawer">
          <div className="filter-actions" aria-label="Graph filter actions">
            <button onClick={selectAllFilters}>Select all</button>
            <button onClick={clearAllFilters}>Clear all</button>
            <button onClick={resetFilters}>Reset filters</button>
          </div>
          <div>
            <span><Filter size={14} /> Node types</span>
            {nodeTypes.map((type) => (
              <label key={type}>
                <input
                  type="checkbox"
                  checked={enabledNodeTypes.has(type)}
                  onChange={() => toggleNodeType(type)}
                />
                <i style={{ background: nodeMeta(type).color }} />
                {nodeMeta(type).label}
              </label>
            ))}
          </div>
          <div>
            <span><GitEdgeIcon /> Edge types</span>
            {edgeTypes.map((type) => (
              <label key={type}>
                <input
                  type="checkbox"
                  checked={enabledEdgeTypes.has(type)}
                  onChange={() => toggleEdgeType(type)}
                />
                <i style={{ background: edgeMeta(type).color }} />
                {edgeMeta(type).label}
              </label>
            ))}
          </div>
        </div>
      ) : null}

      <div className="graph-statistics" aria-label="Visible graph statistics">
        <span><strong>{flowNodes.length}</strong> visible nodes</span>
        <span><strong>{flowEdges.length}</strong> visible edges</span>
        <span><strong>{graph.edges.length}</strong> total graph edges</span>
      </div>

      <div className={`graph-stage ${selectedNode ? "has-detail" : ""}`}>
        <div className="graph-canvas">
          {degraded ? (
            <div className="degradation-note">
              {focusMode
                ? `Showing the ${visibleNodeIds.size}-node neighborhood around ${selectedNode?.label ?? "the selected node"}.`
                : `Showing a connected ${visibleNodeIds.size}-node selection from ${eligibleNodeCount} eligible nodes for smooth rendering.`}
            </div>
          ) : null}
          {highlightLabel ? (
            <button
              className="highlight-note"
              onClick={() => {
                setHighlightedIds(new Set());
                setHighlightLabel(null);
              }}
            >
              <span>{highlightLabel}</span><X size={14} />
            </button>
          ) : null}
          {actionError ? <p className="inline-error">{actionError}</p> : null}
          {edgeGuidance ? (
            <div className="graph-empty-guidance" role="status">
              <strong>No relationships to display</strong>
              <span>{edgeGuidance}</span>
            </div>
          ) : null}
          <ReactFlow
            nodes={flowNodes}
            edges={flowEdges}
            fitView
            minZoom={0.12}
            maxZoom={1.8}
            onNodeClick={(_, flowNode) => {
              const node = graph.nodes.find((item) => item.id === flowNode.id) ?? null;
              onSelectNode(node);
            }}
            onPaneClick={() => onSelectNode(null)}
            nodesDraggable
            proOptions={{ hideAttribution: true }}
          >
            <Background variant={BackgroundVariant.Dots} gap={22} size={1.2} color="#c9d1dc" />
            <Controls showInteractive={false} position="bottom-left" />
            {graph.nodes.length > 40 ? (
              <MiniMap
                pannable
                zoomable
                nodeColor={(node) =>
                  nodeMeta(String(graph.nodes.find((item) => item.id === node.id)?.type)).color
                }
                maskColor="rgba(240, 243, 246, 0.78)"
                position="bottom-right"
              />
            ) : null}
          </ReactFlow>
        </div>
        {selectedNode ? (
          <NodeDetailPanel
            node={selectedNode}
            graph={graph}
            critical={critical}
            onClose={() => onSelectNode(null)}
            onSelectNode={onSelectNode}
            onTrace={() => runAnalysis("trace")}
            onImpact={() => runAnalysis("impact")}
            busyAction={busyAction}
          />
        ) : null}
      </div>

      <details className="accessible-graph-list">
        <summary>Accessible graph node list ({graph.nodes.length})</summary>
        <div>
          {graph.nodes.slice(0, 200).map((node) => (
            <button key={node.id} onClick={() => onSelectNode(node)}>
              <span style={{ background: nodeMeta(node.type).color }}>{nodeMeta(node.type).short}</span>
              <strong>{node.label}</strong>
              <small>{nodePath(node) ?? node.type}</small>
            </button>
          ))}
        </div>
      </details>
    </div>
  );
}

function GitEdgeIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
      <circle cx="2.5" cy="7" r="1.8" fill="currentColor" />
      <circle cx="11.5" cy="3" r="1.8" fill="currentColor" />
      <circle cx="11.5" cy="11" r="1.8" fill="currentColor" />
      <path d="M4 6.4 9.7 3.7M4 7.6l5.7 2.7" stroke="currentColor" strokeWidth="1.2" />
    </svg>
  );
}
