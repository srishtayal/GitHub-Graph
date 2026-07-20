"use client";

import {
  AlertTriangle,
  ArrowUpRight,
  Boxes,
  Braces,
  CircleDot,
  GitBranch,
  Route,
  Sparkles
} from "lucide-react";
import { compactNumber, formatPercent, nodeMeta } from "@/lib/graph-utils";
import type { GraphNode, RepositoryWorkspaceData } from "@/lib/types";

type OverviewDashboardProps = {
  data: RepositoryWorkspaceData;
  onNavigate: (tab: string) => void;
  onSelectNode: (node: GraphNode) => void;
};

export function OverviewDashboard({
  data,
  onNavigate,
  onSelectNode
}: OverviewDashboardProps) {
  const { summary, analysis, graph, critical, components, cycles } = data;
  const snapshot = summary.latestSnapshot;
  const languages = Object.entries(snapshot?.languageSummary ?? {}).sort((a, b) => b[1] - a[1]);
  const languageTotal = languages.reduce((total, [, count]) => total + count, 0);
  const nodeCounts = graph.nodes.reduce<Record<string, number>>((counts, node) => {
    counts[node.type] = (counts[node.type] ?? 0) + 1;
    return counts;
  }, {});

  return (
    <div className="workspace-view overview-view animate-in">
      <section className="overview-hero">
        <div>
          <div className="section-kicker">
            <CircleDot size={14} />
            Analysis complete
          </div>
          <h2>
            Your repository,
            <br />
            mapped end to end.
          </h2>
          <p>
            Explore {compactNumber(graph.nodes.length)} code entities and{" "}
            {compactNumber(graph.edges.length)} relationships from snapshot{" "}
            <code>{snapshot?.commitSha.slice(0, 8)}</code>.
          </p>
        </div>
        <button className="hero-action" onClick={() => onNavigate("graph")}>
          <span>
            Open graph explorer
            <small>Pan, filter, trace and inspect</small>
          </span>
          <ArrowUpRight size={22} />
        </button>
      </section>

      <section className="metric-ribbon" aria-label="Repository metrics">
        <article>
          <span className="metric-icon coral"><Braces size={19} /></span>
          <div><strong>{compactNumber(analysis.summary.totalFunctions)}</strong><span>Functions</span></div>
          <small>{compactNumber(analysis.summary.totalClasses)} classes</small>
        </article>
        <article>
          <span className="metric-icon teal"><GitBranch size={19} /></span>
          <div><strong>{compactNumber(graph.edges.length)}</strong><span>Relationships</span></div>
          <small>{compactNumber(analysis.summary.totalMethodCalls)} calls parsed</small>
        </article>
        <article>
          <span className="metric-icon blue"><Boxes size={19} /></span>
          <div><strong>{components.totalComponents}</strong><span>Components</span></div>
          <small>{compactNumber(graph.nodes.length)} total nodes</small>
        </article>
        <article>
          <span className={`metric-icon ${cycles.hasCycles ? "amber" : "green"}`}>
            <AlertTriangle size={19} />
          </span>
          <div><strong>{cycles.totalCycles}</strong><span>Cycles</span></div>
          <small>{cycles.hasCycles ? "Review recommended" : "Dependency order is clean"}</small>
        </article>
      </section>

      <div className="dashboard-grid">
        <section className="surface-card language-card">
          <div className="card-heading">
            <div>
              <p className="card-eyebrow">Composition</p>
              <h3>Language footprint</h3>
            </div>
            <span className="count-chip">{snapshot?.totalFiles ?? 0} files</span>
          </div>
          <div className="language-track" aria-label="Language distribution">
            {languages.map(([language, count], index) => (
              <span
                key={language}
                className={`language-segment language-${index % 5}`}
                style={{ width: `${Math.max((count / Math.max(languageTotal, 1)) * 100, 3)}%` }}
                title={`${language}: ${count} files`}
              />
            ))}
          </div>
          <div className="language-list">
            {languages.slice(0, 6).map(([language, count], index) => (
              <div key={language}>
                <span className={`legend-dot language-bg-${index % 5}`} />
                <strong>{language}</strong>
                <span>{formatPercent(count / Math.max(languageTotal, 1))}</span>
                <small>{count} files</small>
              </div>
            ))}
            {languages.length === 0 ? <p className="empty-copy">No language data reported.</p> : null}
          </div>
        </section>

        <section className="surface-card critical-card">
          <div className="card-heading">
            <div>
              <p className="card-eyebrow">Risk signal</p>
              <h3>Most connected code</h3>
            </div>
            <button className="text-button" onClick={() => onNavigate("dependencies")}>
              View impact map <ArrowUpRight size={15} />
            </button>
          </div>
          <div className="critical-list">
            {critical.nodes.slice(0, 5).map((item, index) => (
              <button key={item.node.id} onClick={() => onSelectNode(item.node)}>
                <span className="rank-number">{String(index + 1).padStart(2, "0")}</span>
                <span
                  className="node-type-mark"
                  style={{ background: nodeMeta(item.node.type).color }}
                >
                  {nodeMeta(item.node.type).short}
                </span>
                <span className="critical-name">
                  <strong>{item.node.label}</strong>
                  <small>{item.inDegree} incoming · {item.outDegree} outgoing</small>
                </span>
                <span className="centrality-score">
                  {formatPercent(item.degreeCentrality)}
                </span>
              </button>
            ))}
          </div>
        </section>

        <section className="surface-card node-mix-card">
          <div className="card-heading">
            <div>
              <p className="card-eyebrow">Graph anatomy</p>
              <h3>Node distribution</h3>
            </div>
            <CircleDot size={18} />
          </div>
          <div className="node-mix">
            {Object.entries(nodeCounts)
              .sort((a, b) => b[1] - a[1])
              .map(([type, count]) => {
                const meta = nodeMeta(type);
                return (
                  <button key={type} onClick={() => onNavigate("graph")}>
                    <span style={{ "--node-color": meta.color } as React.CSSProperties}>
                      {meta.short}
                    </span>
                    <strong>{compactNumber(count)}</strong>
                    <small>{meta.label}</small>
                  </button>
                );
              })}
          </div>
        </section>

        <section className="surface-card intelligence-card">
          <div className="intelligence-copy">
            <span className="metric-icon ink"><Sparkles size={19} /></span>
            <div>
              <p className="card-eyebrow">Repository intelligence</p>
              <h3>Move from counts to answers.</h3>
              <p>
                Compare implementation patterns, localize failures, or ask a grounded question
                backed by graph evidence.
              </p>
            </div>
          </div>
          <div className="intelligence-actions">
            <button onClick={() => onNavigate("similarity")}>
              <GitBranch size={18} />
              Find similar functions
            </button>
            <button onClick={() => onNavigate("errors")}>
              <AlertTriangle size={18} />
              Analyze an error
            </button>
            <button onClick={() => onNavigate("ask")}>
              <Route size={18} />
              Explain a flow
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}
