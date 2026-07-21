"use client";

import { useEffect, useMemo, useRef, useState } from "react";
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
  ArrowLeft,
  ArrowRight,
  Boxes,
  ChevronRight,
  CircleHelp,
  FileCode2,
  FolderOpen,
  GitBranch,
  Layers3,
  Network,
  PackageOpen,
  X
} from "lucide-react";
import {
  getRepositoryGraphComponent,
  getRepositoryGraphOverview
} from "@/lib/api-client";
import {
  buildLayeredProjection,
  formatProjectedEdgeLabel,
  projectedEdgeBreakdown,
  projectionSymbolCount
} from "@/lib/graph-projection";
import type {
  GraphProjection,
  GraphProjectionEdge,
  GraphProjectionNode
} from "@/lib/types";

type RepositoryProjectionExplorerProps = {
  repositoryId: string;
  repositoryName: string;
  onOpenDetailed: () => void;
};

const CATEGORY_COLORS: Record<string, string> = {
  "source-area": "#2a9d8f",
  source: "#2a9d8f",
  testing: "#4e7cff",
  documentation: "#d9972f",
  "build-ci": "#de6b48",
  "external-dependencies": "#7d8795",
  supporting: "#a56b46",
  "supporting-files": "#a56b46",
  directory: "#6d8e75",
  file: "#2a9d8f",
  module: "#8a9f74"
};

export function RepositoryProjectionExplorer({
  repositoryId,
  repositoryName,
  onOpenDetailed
}: RepositoryProjectionExplorerProps) {
  const [overview, setOverview] = useState<GraphProjection | null>(null);
  const [projection, setProjection] = useState<GraphProjection | null>(null);
  const [componentName, setComponentName] = useState<string | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
  const [hoveredEdgeId, setHoveredEdgeId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const nodeClickTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastNodeClickRef = useRef<{ nodeId: string; clickedAt: number } | null>(null);
  const openingComponentIdRef = useRef<string | null>(null);

  useEffect(() => () => {
    if (nodeClickTimerRef.current) clearTimeout(nodeClickTimerRef.current);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setOverview(null);
    setProjection(null);
    setComponentName(null);
    setSelectedNodeId(null);
    setSelectedEdgeId(null);

    getRepositoryGraphOverview(repositoryId)
      .then((response) => {
        if (cancelled) return;
        setOverview(response);
        setProjection(response);
        setSelectedNodeId(mostImportantNode(response.nodes)?.id ?? null);
      })
      .catch((reason) => {
        if (cancelled) return;
        setError(reason instanceof Error ? reason.message : "Unable to load the repository overview.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [repositoryId]);

  async function openComponent(node: GraphProjectionNode) {
    if (
      !node.expandable ||
      projection?.level !== "OVERVIEW" ||
      openingComponentIdRef.current === node.id
    ) return;
    openingComponentIdRef.current = node.id;
    setLoading(true);
    setError(null);
    try {
      const response = await getRepositoryGraphComponent(repositoryId, node.id);
      setProjection(response);
      setComponentName(node.displayName);
      setSelectedNodeId(mostImportantNode(response.nodes)?.id ?? null);
      setSelectedEdgeId(null);
      setHoveredEdgeId(null);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to open this component.");
    } finally {
      openingComponentIdRef.current = null;
      setLoading(false);
    }
  }

  function returnToOverview() {
    if (!overview) return;
    setProjection(overview);
    setComponentName(null);
    setSelectedNodeId(mostImportantNode(overview.nodes)?.id ?? null);
    setSelectedEdgeId(null);
    setHoveredEdgeId(null);
    setError(null);
  }

  function selectNodeAfterClick(nodeId: string) {
    const now = Date.now();
    const previousClick = lastNodeClickRef.current;
    if (previousClick?.nodeId === nodeId && now - previousClick.clickedAt <= 450) {
      lastNodeClickRef.current = null;
      openNodeAfterDoubleClick(nodeId);
      return;
    }
    lastNodeClickRef.current = { nodeId, clickedAt: now };
    if (nodeClickTimerRef.current) clearTimeout(nodeClickTimerRef.current);
    nodeClickTimerRef.current = setTimeout(() => {
      setSelectedNodeId(nodeId);
      setSelectedEdgeId(null);
      nodeClickTimerRef.current = null;
    }, 220);
  }

  function openNodeAfterDoubleClick(nodeId: string) {
    if (nodeClickTimerRef.current) {
      clearTimeout(nodeClickTimerRef.current);
      nodeClickTimerRef.current = null;
    }
    lastNodeClickRef.current = null;
    const node = nodeById.get(nodeId);
    if (node) void openComponent(node);
  }

  const nodeById = useMemo(
    () => new Map((projection?.nodes ?? []).map((node) => [node.id, node])),
    [projection?.nodes]
  );
  const positions = useMemo(
    () => buildLayeredProjection(projection?.nodes ?? [], projection?.edges ?? []),
    [projection?.edges, projection?.nodes]
  );
  const maximumCriticality = Math.max(
    0.000001,
    ...(projection?.nodes ?? []).map((node) => node.criticalityScore)
  );
  const maximumRelationships = Math.max(
    1,
    ...(projection?.edges ?? []).map((edge) => edge.totalRelationshipCount)
  );
  const activeEdgeId = hoveredEdgeId ?? selectedEdgeId;

  const flowNodes = useMemo<Node[]>(
    () =>
      (projection?.nodes ?? []).map((node) => {
        const emphasis = Math.sqrt(node.criticalityScore / maximumCriticality);
        const color = categoryColor(node.category);
        return {
          id: node.id,
          position: positions.get(node.id) ?? { x: 0, y: 0 },
          data: {
            label: (
              <div
                className="projection-node-content"
                onDoubleClick={(event) => {
                  event.stopPropagation();
                  openNodeAfterDoubleClick(node.id);
                }}
              >
                <div className="projection-node-topline">
                  <span>{purposeCategory(node.category)}</span>
                  <i>{node.incomingDependencyCount + node.outgoingDependencyCount} deps</i>
                </div>
                <strong>{node.displayName}</strong>
                <div className="projection-node-metrics">
                  <span><FileCode2 size={12} /> {node.counts.files} files</span>
                  <span><Boxes size={12} /> {projectionSymbolCount(node)} symbols</span>
                </div>
              </div>
            )
          },
          className: `projection-flow-node${selectedNodeId === node.id ? " is-selected" : ""}`,
          zIndex: selectedNodeId === node.id ? 20 : 10,
          style: {
            width: 226 + emphasis * 28,
            minHeight: 116 + emphasis * 10,
            "--projection-color": color,
            "--projection-emphasis": emphasis
          } as React.CSSProperties,
          sourcePosition: Position.Right,
          targetPosition: Position.Left
        };
      }),
    [maximumCriticality, positions, projection?.nodes, selectedNodeId]
  );

  const flowEdges = useMemo<Edge[]>(
    () =>
      (projection?.edges ?? []).map((edge) => {
        const active = activeEdgeId === edge.id;
        const weight = Math.sqrt(edge.totalRelationshipCount / maximumRelationships);
        return {
          id: edge.id,
          source: edge.source,
          target: edge.target,
          type: "smoothstep",
          label: <span className="projection-edge-label">{formatProjectedEdgeLabel(edge)}</span>,
          markerEnd: {
            type: MarkerType.ArrowClosed,
            width: 17,
            height: 17,
            color: active ? "#ff6b4a" : "#667487"
          },
          zIndex: active ? 5 : 1,
          style: {
            stroke: active ? "#ff6b4a" : "#667487",
            strokeWidth: 1.4 + weight * 4.6,
            opacity: active ? 1 : 0.68
          },
          labelStyle: { fill: "#263245", fontSize: 9, fontWeight: 800 },
          labelBgStyle: { fill: "#fffdf7", fillOpacity: 0.94 },
          labelBgPadding: [5, 3] as [number, number],
          labelBgBorderRadius: 5
        };
      }),
    [activeEdgeId, maximumRelationships, projection?.edges]
  );

  const selectedNode = selectedNodeId ? nodeById.get(selectedNodeId) ?? null : null;
  const selectedEdge = projection?.edges.find((edge) => edge.id === selectedEdgeId) ?? null;
  const hoveredEdge = projection?.edges.find((edge) => edge.id === hoveredEdgeId) ?? null;
  const inspectedEdge = hoveredEdge ?? selectedEdge;
  const hasDetail = Boolean(selectedNode || selectedEdge);

  return (
    <div className="workspace-view graph-view projection-view animate-in">
      <div className="view-heading graph-heading projection-heading">
        <div>
          <p className="section-kicker"><Layers3 size={14} /> Repository architecture</p>
          <h2>{projection?.level === "COMPONENT" ? "Open one area at a time." : "See the system before the symbols."}</h2>
          <p>
            {projection?.level === "COMPONENT"
              ? "This component view exposes its files, modules and internal dependency flow."
              : "Major components summarize the repository; arrows show how those areas depend on one another."}
          </p>
        </div>
        <div className="projection-heading-actions">
          {projection?.level === "COMPONENT" ? (
            <button className="toolbar-button" onClick={returnToOverview}>
              <ArrowLeft size={15} /> Repository overview
            </button>
          ) : null}
          <button className="toolbar-button" onClick={onOpenDetailed}>
            <Network size={15} /> Detailed graph
          </button>
        </div>
      </div>

      <nav className="projection-breadcrumb" aria-label="Graph level">
        <button onClick={returnToOverview} disabled={!overview}>{repositoryName}</button>
        <ChevronRight size={13} />
        <strong>{componentName ?? "Repository overview"}</strong>
      </nav>

      <div className="projection-meta-row">
        <div className="graph-statistics" aria-label="Projection statistics">
          <span><strong>{projection?.nodes.length ?? 0}</strong> components</span>
          <span><strong>{projection?.edges.length ?? 0}</strong> dependencies</span>
          <span><strong>{projection?.totals.rawNodeCount ?? 0}</strong> underlying nodes</span>
        </div>
        <div className="projection-legend" aria-label="Repository overview legend">
          <span><i className="legend-component" /> Node = repository area</span>
          <span><i className="legend-weight" /> Thicker = more relationships</span>
          <span><ArrowRight size={13} /> Arrow = dependency direction</span>
        </div>
      </div>

      {error ? <div className="projection-error" role="alert">{error}</div> : null}
      <div className={`graph-stage projection-stage ${hasDetail ? "has-detail" : ""}`}>
        <div className="graph-canvas projection-canvas">
          {loading ? (
            <div className="projection-loading" role="status">
              <span /><strong>Mapping repository components…</strong>
            </div>
          ) : null}
          {!loading && projection?.nodes.length === 0 ? (
            <div className="graph-empty-guidance" role="status">
              <strong>No components available</strong>
              <span>This snapshot does not contain enough indexed structure for a repository overview.</span>
            </div>
          ) : null}
          {inspectedEdge ? (
            <EdgeInspector edge={inspectedEdge} nodeById={nodeById} pinned={selectedEdgeId === inspectedEdge.id} />
          ) : null}
          <ReactFlow
            key={`${projection?.level ?? "loading"}:${projection?.rootId ?? repositoryId}`}
            nodes={flowNodes}
            edges={flowEdges}
            fitView
            fitViewOptions={{ padding: 0.18, minZoom: 0.35, maxZoom: 1.05 }}
            minZoom={0.2}
            maxZoom={1.5}
            nodesDraggable={false}
            nodesConnectable={false}
            elementsSelectable
            onNodeClick={(_, flowNode) => selectNodeAfterClick(flowNode.id)}
            onNodeDoubleClick={(_, flowNode) => openNodeAfterDoubleClick(flowNode.id)}
            onEdgeClick={(_, flowEdge) => {
              setSelectedEdgeId(flowEdge.id);
              setSelectedNodeId(null);
            }}
            onEdgeMouseEnter={(_, flowEdge) => setHoveredEdgeId(flowEdge.id)}
            onEdgeMouseLeave={() => setHoveredEdgeId(null)}
            onPaneClick={() => {
              setSelectedNodeId(null);
              setSelectedEdgeId(null);
            }}
            proOptions={{ hideAttribution: true }}
          >
            <Background variant={BackgroundVariant.Dots} gap={24} size={1.15} color="#cbd4d3" />
            <Controls showInteractive={false} position="bottom-left" />
            {(projection?.nodes.length ?? 0) > 15 ? (
              <MiniMap
                pannable
                zoomable
                nodeColor={(node) => categoryColor(nodeById.get(node.id)?.category ?? "file")}
                maskColor="rgba(240, 243, 246, 0.78)"
                position="bottom-right"
              />
            ) : null}
          </ReactFlow>
        </div>
        {projection && selectedNode ? (
          <ProjectionNodePanel
            projection={projection}
            node={selectedNode}
            nodeById={nodeById}
            onClose={() => setSelectedNodeId(null)}
            onSelectNode={(nodeId) => {
              setSelectedNodeId(nodeId);
              setSelectedEdgeId(null);
            }}
            onOpenComponent={() => void openComponent(selectedNode)}
          />
        ) : null}
        {projection && selectedEdge ? (
          <ProjectionEdgePanel
            edge={selectedEdge}
            nodeById={nodeById}
            onClose={() => setSelectedEdgeId(null)}
            onSelectNode={(nodeId) => {
              setSelectedNodeId(nodeId);
              setSelectedEdgeId(null);
            }}
          />
        ) : null}
      </div>

      <details className="accessible-graph-list projection-accessible-list">
        <summary>Accessible component list ({projection?.nodes.length ?? 0})</summary>
        <div>
          {(projection?.nodes ?? []).map((node) => (
            <button key={node.id} onClick={() => setSelectedNodeId(node.id)}>
              <span style={{ background: categoryColor(node.category) }}>C</span>
              <strong>{node.displayName}</strong>
              <small>{node.counts.files} files · {projectionSymbolCount(node)} symbols</small>
            </button>
          ))}
        </div>
      </details>
    </div>
  );
}

function ProjectionNodePanel({
  projection,
  node,
  nodeById,
  onClose,
  onSelectNode,
  onOpenComponent
}: {
  projection: GraphProjection;
  node: GraphProjectionNode;
  nodeById: Map<string, GraphProjectionNode>;
  onClose: () => void;
  onSelectNode: (nodeId: string) => void;
  onOpenComponent: () => void;
}) {
  const relationships = projection.edges
    .filter((edge) => edge.source === node.id || edge.target === node.id)
    .sort((left, right) => right.totalRelationshipCount - left.totalRelationshipCount);
  const color = categoryColor(node.category);

  return (
    <aside className="node-detail-panel projection-detail-panel" aria-label={`${node.displayName} component details`}>
      <div className="detail-header">
        <span className="detail-node-icon" style={{ background: color }}><PackageOpen size={18} /></span>
        <button className="icon-button" onClick={onClose} aria-label="Close component details"><X size={17} /></button>
      </div>
      <p className="card-eyebrow">{purposeCategory(node.category)}</p>
      <h3>{node.displayName}</h3>
      <p className="projection-purpose">{purposeDescription(node.category)}</p>

      {projection.level === "OVERVIEW" && node.expandable ? (
        <button className="open-component-button" onClick={onOpenComponent}>
          <FolderOpen size={15} /> Open component <ArrowRight size={14} />
        </button>
      ) : null}

      <div className="detail-score-grid projection-score-grid">
        <div><strong>{node.counts.files}</strong><span>Files</span></div>
        <div><strong>{projectionSymbolCount(node)}</strong><span>Symbols</span></div>
        <div><strong>{node.incomingDependencyCount + node.outgoingDependencyCount}</strong><span>Dependencies</span></div>
      </div>

      <section className="projection-panel-section">
        <h4><FileCode2 size={14} /> Representative contents</h4>
        {node.representatives.length > 0 ? (
          <div className="projection-representatives">
            {node.representatives.map((reference) => (
              <div key={reference.id}>
                <span>{reference.type.slice(0, 1).toUpperCase()}</span>
                <p><strong>{reference.displayName}</strong><small>{reference.type}</small></p>
              </div>
            ))}
          </div>
        ) : <p className="projection-muted">No representative files or symbols are available.</p>}
      </section>

      <section className="projection-panel-section">
        <h4><GitBranch size={14} /> Component dependencies</h4>
        {relationships.length > 0 ? relationships.slice(0, 8).map((edge) => {
          const outgoing = edge.source === node.id;
          const peerId = outgoing ? edge.target : edge.source;
          const peer = nodeById.get(peerId);
          return (
            <button key={edge.id} className="projection-relationship" onClick={() => onSelectNode(peerId)}>
              <span>{outgoing ? "→" : "←"}</span>
              <p><strong>{peer?.displayName ?? peerId}</strong><small>{formatProjectedEdgeLabel(edge)}</small></p>
              <em>{edge.totalRelationshipCount}</em>
            </button>
          );
        }) : <p className="projection-muted">No cross-component dependencies were extracted.</p>}
      </section>
    </aside>
  );
}

function ProjectionEdgePanel({
  edge,
  nodeById,
  onClose,
  onSelectNode
}: {
  edge: GraphProjectionEdge;
  nodeById: Map<string, GraphProjectionNode>;
  onClose: () => void;
  onSelectNode: (nodeId: string) => void;
}) {
  const source = nodeById.get(edge.source);
  const target = nodeById.get(edge.target);
  return (
    <aside className="node-detail-panel projection-detail-panel" aria-label="Dependency details">
      <div className="detail-header">
        <span className="detail-node-icon projection-edge-icon"><GitBranch size={18} /></span>
        <button className="icon-button" onClick={onClose} aria-label="Close dependency details"><X size={17} /></button>
      </div>
      <p className="card-eyebrow">Aggregated dependency</p>
      <h3>{source?.displayName ?? edge.source}</h3>
      <div className="projection-edge-direction"><ArrowRight size={16} /><strong>{target?.displayName ?? edge.target}</strong></div>
      <p className="projection-purpose">This arrow combines {edge.totalRelationshipCount} code-level relationships between these areas.</p>
      <div className="projection-breakdown">
        {projectedEdgeBreakdown(edge).map((item) => <span key={item}>{item}</span>)}
      </div>
      <div className="detail-actions projection-edge-actions">
        <button onClick={() => onSelectNode(edge.source)}>Inspect source</button>
        <button onClick={() => onSelectNode(edge.target)}>Inspect target</button>
      </div>
      <section className="projection-panel-section">
        <h4><CircleHelp size={14} /> Traceability</h4>
        <p className="projection-muted">Backed by {edge.underlyingEdgeIds.length} raw graph edge IDs. The aggregate never invents a relationship.</p>
      </section>
    </aside>
  );
}

function EdgeInspector({
  edge,
  nodeById,
  pinned
}: {
  edge: GraphProjectionEdge;
  nodeById: Map<string, GraphProjectionNode>;
  pinned: boolean;
}) {
  return (
    <div className="projection-edge-inspector" role="status">
      <span>{pinned ? "Selected dependency" : "Dependency"}</span>
      <strong>{nodeById.get(edge.source)?.displayName ?? edge.source} → {nodeById.get(edge.target)?.displayName ?? edge.target}</strong>
      <small>{projectedEdgeBreakdown(edge).join(" · ")}</small>
    </div>
  );
}

function mostImportantNode(nodes: GraphProjectionNode[]): GraphProjectionNode | null {
  return [...nodes].sort((left, right) =>
    right.criticalityScore - left.criticalityScore ||
    (right.incomingDependencyCount + right.outgoingDependencyCount) -
      (left.incomingDependencyCount + left.outgoingDependencyCount) ||
    left.displayName.localeCompare(right.displayName)
  )[0] ?? null;
}

function categoryColor(category: string): string {
  return CATEGORY_COLORS[category.toLowerCase()] ?? "#63758a";
}

function purposeCategory(category: string): string {
  const labels: Record<string, string> = {
    "source-area": "Core capability",
    source: "Application source",
    testing: "Quality assurance",
    documentation: "Documentation",
    "build-ci": "Build and delivery",
    "external-dependencies": "External platform",
    supporting: "Project support",
    "supporting-files": "Project support",
    directory: "Repository area",
    file: "Source file",
    module: "Imported module"
  };
  return labels[category.toLowerCase()] ?? "Repository component";
}

function purposeDescription(category: string): string {
  const descriptions: Record<string, string> = {
    "source-area": "A cohesive source-code capability detected from the Python package structure.",
    source: "The primary application or library implementation.",
    testing: "Tests and fixtures that verify repository behavior.",
    documentation: "Guides, references and explanatory project material.",
    "build-ci": "Automation, packaging and continuous-integration configuration.",
    "external-dependencies": "Third-party modules imported by repository code.",
    supporting: "Repository-level files that support development and distribution.",
    "supporting-files": "Repository-level files that support development and distribution.",
    file: "A file inside the selected repository component.",
    module: "An external module connected to this component."
  };
  return descriptions[category.toLowerCase()] ?? "A deterministic group of related repository nodes.";
}
