"use client";

import { useState } from "react";
import {
  ArrowDown,
  ArrowRight,
  GitBranch,
  Network,
  Play,
  ShieldAlert
} from "lucide-react";
import { getDependencyPath, getImpactAnalysis } from "@/lib/api-client";
import { nodeMeta, nodePath } from "@/lib/graph-utils";
import type {
  DependencyPathResponse,
  GraphNode,
  ImpactAnalysisResponse,
  RepositoryGraph
} from "@/lib/types";
import { NodePicker } from "./node-picker";

type DependencyViewProps = {
  repositoryId: string;
  graph: RepositoryGraph;
  selectedNode: GraphNode | null;
  onSelectNode: (node: GraphNode) => void;
  onOpenGraph: () => void;
};

export function DependencyView({
  repositoryId,
  graph,
  selectedNode,
  onSelectNode,
  onOpenGraph
}: DependencyViewProps) {
  const fallback = graph.nodes.find((node) => node.type === "function") ?? graph.nodes[0] ?? null;
  const target = selectedNode ?? fallback;
  const [trace, setTrace] = useState<DependencyPathResponse | null>(null);
  const [impact, setImpact] = useState<ImpactAnalysisResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function analyze() {
    if (!target) return;
    setLoading(true);
    setError(null);
    try {
      const [nextTrace, nextImpact] = await Promise.all([
        getDependencyPath(repositoryId, target.id),
        getImpactAnalysis(repositoryId, target.id)
      ]);
      setTrace(nextTrace);
      setImpact(nextImpact);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to analyze this node.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="workspace-view dependency-view animate-in">
      <div className="view-heading split-heading">
        <div>
          <p className="section-kicker"><GitBranch size={14} /> Dependency intelligence</p>
          <h2>Trace what it needs. Map what it can break.</h2>
          <p>DFS follows dependencies in depth; BFS maps the downstream blast radius.</p>
        </div>
        <button className="secondary-button" onClick={onOpenGraph}>
          <Network size={17} /> Open visual graph
        </button>
      </div>

      <section className="analysis-control-card">
        <NodePicker
          nodes={graph.nodes}
          selectedNode={target}
          onSelectNode={(node) => {
            onSelectNode(node);
            setTrace(null);
            setImpact(null);
          }}
          label="Analyze a code entity"
        />
        <button className="primary-button compact" onClick={analyze} disabled={!target || loading}>
          <Play size={16} fill="currentColor" />
          {loading ? "Running graph algorithms…" : "Trace dependencies + impact"}
        </button>
      </section>
      {error ? <p className="inline-error">{error}</p> : null}

      {!trace && !impact ? (
        <section className="analysis-empty">
          <div className="pulse-orbit"><span /><span /><i /></div>
          <h3>Choose a node to reveal its dependency story.</h3>
          <p>The result will show ordered traversal evidence, depth, edge types and blast radius.</p>
        </section>
      ) : (
        <div className="dependency-results">
          <TraversalColumn
            title="Dependency trace"
            eyebrow="Depth-first search"
            description="What this node reaches through outgoing relationships."
            icon={<GitBranch size={18} />}
            records={trace?.traversalOrder ?? []}
            accent="blue"
            onSelectNode={onSelectNode}
          />
          <TraversalColumn
            title="Impact radius"
            eyebrow="Breadth-first search"
            description={`${impact?.totalAffectedNodes ?? 0} downstream nodes can be affected.`}
            icon={<ShieldAlert size={18} />}
            records={impact?.affectedNodes ?? []}
            accent="coral"
            onSelectNode={onSelectNode}
          />
        </div>
      )}
    </div>
  );
}

type TraversalColumnProps = {
  title: string;
  eyebrow: string;
  description: string;
  icon: React.ReactNode;
  records: Array<{
    node: GraphNode;
    depth: number;
    predecessorNodeId: string | null;
    viaEdgeType: string | null;
  }>;
  accent: "blue" | "coral";
  onSelectNode: (node: GraphNode) => void;
};

function TraversalColumn({
  title,
  eyebrow,
  description,
  icon,
  records,
  accent,
  onSelectNode
}: TraversalColumnProps) {
  const maxDepth = Math.max(...records.map((record) => record.depth), 0);
  return (
    <section className={`traversal-column accent-${accent}`}>
      <div className="traversal-heading">
        <span>{icon}</span>
        <div><p>{eyebrow}</p><h3>{title}</h3><small>{description}</small></div>
      </div>
      <div className="traversal-summary">
        <span><strong>{Math.max(records.length - 1, 0)}</strong> related nodes</span>
        <span><strong>{maxDepth}</strong> max depth</span>
      </div>
      <div className="traversal-path">
        {records.slice(0, 18).map((record, index) => (
          <div key={record.node.id}>
            {index > 0 ? <ArrowDown size={15} className="path-arrow" /> : null}
            <button onClick={() => onSelectNode(record.node)}>
              <span
                className="node-type-mark"
                style={{ background: nodeMeta(record.node.type).color }}
              >
                {nodeMeta(record.node.type).short}
              </span>
              <span>
                <strong>{record.node.label}</strong>
                <small>{nodePath(record.node) ?? nodeMeta(record.node.type).label}</small>
              </span>
              <span className="depth-badge">d{record.depth}</span>
              {record.viaEdgeType ? <em>{record.viaEdgeType}<ArrowRight size={12} /></em> : null}
            </button>
          </div>
        ))}
        {records.length > 18 ? <p className="more-copy">+ {records.length - 18} more nodes</p> : null}
      </div>
    </section>
  );
}
