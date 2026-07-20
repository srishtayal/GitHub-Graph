"use client";

import {
  ArrowDownLeft,
  ArrowUpRight,
  FileCode2,
  GitBranch,
  Network,
  Route,
  X
} from "lucide-react";
import {
  formatPercent,
  graphIndex,
  nodeMeta,
  nodePath,
  nodeQualifiedName,
  propertyText
} from "@/lib/graph-utils";
import type {
  CriticalNodesResponse,
  GraphNode,
  RepositoryGraph
} from "@/lib/types";

type NodeDetailPanelProps = {
  node: GraphNode;
  graph: RepositoryGraph;
  critical: CriticalNodesResponse;
  onClose: () => void;
  onSelectNode: (node: GraphNode) => void;
  onTrace: () => void;
  onImpact: () => void;
  busyAction?: "trace" | "impact" | null;
};

export function NodeDetailPanel({
  node,
  graph,
  critical,
  onClose,
  onSelectNode,
  onTrace,
  onImpact,
  busyAction
}: NodeDetailPanelProps) {
  const index = graphIndex(graph);
  const incoming = index.incoming.get(node.id) ?? [];
  const outgoing = index.outgoing.get(node.id) ?? [];
  const centralityIndex = critical.nodes.findIndex((item) => item.node.id === node.id);
  const centrality = centralityIndex >= 0 ? critical.nodes[centralityIndex] : null;
  const path = nodePath(node);
  const meta = nodeMeta(node.type);
  const visibleProperties = Object.entries(node.properties).filter(
    ([key, value]) =>
      !["name", "qualifiedName", "relativePath"].includes(key) &&
      value !== null &&
      value !== ""
  );

  return (
    <aside className="node-detail-panel" aria-label={`Details for ${node.label}`}>
      <div className="detail-header">
        <div className="detail-node-icon" style={{ background: meta.color }}>
          {meta.short}
        </div>
        <button className="icon-button" onClick={onClose} aria-label="Close node details">
          <X size={18} />
        </button>
      </div>
      <p className="card-eyebrow">{meta.label}</p>
      <h3>{node.label}</h3>
      <p className="qualified-name">{nodeQualifiedName(node)}</p>
      {path ? (
        <p className="file-reference">
          <FileCode2 size={15} />
          {path}
          {typeof node.properties.startLine === "number"
            ? `:${node.properties.startLine}`
            : ""}
        </p>
      ) : null}

      <div className="detail-actions">
        <button onClick={onTrace} disabled={Boolean(busyAction)}>
          <GitBranch size={17} />
          {busyAction === "trace" ? "Tracing…" : "Dependencies"}
        </button>
        <button onClick={onImpact} disabled={Boolean(busyAction)}>
          <Network size={17} />
          {busyAction === "impact" ? "Mapping…" : "Impact"}
        </button>
      </div>

      <div className="detail-score-grid">
        <div>
          <strong>{incoming.length}</strong>
          <span>Incoming</span>
        </div>
        <div>
          <strong>{outgoing.length}</strong>
          <span>Outgoing</span>
        </div>
        <div>
          <strong>
            {centrality ? formatPercent(centrality.degreeCentrality) : "—"}
          </strong>
          <span>Centrality</span>
        </div>
      </div>
      {centrality ? (
        <p className="rank-callout">
          Ranked #{centralityIndex + 1} among the most connected nodes.
        </p>
      ) : null}

      <RelationshipList
        title="Calls / uses"
        icon={<ArrowUpRight size={15} />}
        edges={outgoing}
        nodes={index.nodes}
        onSelectNode={onSelectNode}
        targetKey="target"
      />
      <RelationshipList
        title="Called / used by"
        icon={<ArrowDownLeft size={15} />}
        edges={incoming}
        nodes={index.nodes}
        onSelectNode={onSelectNode}
        targetKey="source"
      />

      {visibleProperties.length > 0 ? (
        <div className="property-list">
          <h4><Route size={15} /> Metadata</h4>
          {visibleProperties.slice(0, 8).map(([key, value]) => (
            <div key={key}>
              <span>{key.replace(/([A-Z])/g, " $1")}</span>
              <code>{propertyText(value)}</code>
            </div>
          ))}
        </div>
      ) : null}

      <div className="stable-id">
        <span>Stable graph ID</span>
        <code title={node.id}>{node.id}</code>
      </div>
    </aside>
  );
}

type RelationshipListProps = {
  title: string;
  icon: React.ReactNode;
  edges: Array<{ id: string; type: string; source: string; target: string }>;
  nodes: Map<string, GraphNode>;
  onSelectNode: (node: GraphNode) => void;
  targetKey: "source" | "target";
};

function RelationshipList({
  title,
  icon,
  edges,
  nodes,
  onSelectNode,
  targetKey
}: RelationshipListProps) {
  return (
    <div className="relationship-list">
      <div className="relationship-title">
        <h4>{icon}{title}</h4>
        <span>{edges.length}</span>
      </div>
      {edges.slice(0, 6).map((edge) => {
        const related = nodes.get(edge[targetKey]);
        return related ? (
          <button key={edge.id} onClick={() => onSelectNode(related)}>
            <span
              className="relationship-dot"
              style={{ background: nodeMeta(related.type).color }}
            />
            <span><strong>{related.label}</strong><small>{edge.type}</small></span>
            <ArrowUpRight size={14} />
          </button>
        ) : null;
      })}
      {edges.length === 0 ? <p className="empty-copy">No relationships in this direction.</p> : null}
      {edges.length > 6 ? <p className="more-copy">+ {edges.length - 6} more relationships</p> : null}
    </div>
  );
}
