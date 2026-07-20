"use client";

import { useEffect, useState } from "react";
import { Boxes, GitCompareArrows, LoaderCircle, Play, Sparkles } from "lucide-react";
import { getClusters, getSimilarity } from "@/lib/api-client";
import { formatPercent, nodeMeta, nodePath } from "@/lib/graph-utils";
import type {
  ClusterResult,
  GraphNode,
  RepositoryGraph,
  SimilarityRanking
} from "@/lib/types";
import { NodePicker } from "./node-picker";

type SimilarityViewProps = {
  repositoryId: string;
  graph: RepositoryGraph;
  selectedNode: GraphNode | null;
  onSelectNode: (node: GraphNode) => void;
};

export function SimilarityView({
  repositoryId,
  graph,
  selectedNode,
  onSelectNode
}: SimilarityViewProps) {
  const functions = graph.nodes.filter((node) => node.type === "function");
  const target = selectedNode?.type === "function" ? selectedNode : functions[0] ?? null;
  const [ranking, setRanking] = useState<SimilarityRanking | null>(null);
  const [clusters, setClusters] = useState<ClusterResult | null>(null);
  const [threshold, setThreshold] = useState(0.5);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nodeMap = new Map(graph.nodes.map((node) => [node.id, node]));

  useEffect(() => {
    setRanking(null);
  }, [target?.id]);

  async function compare() {
    if (!target) return;
    setLoading(true);
    setError(null);
    try {
      const [nextRanking, nextClusters] = await Promise.all([
        getSimilarity(repositoryId, target.id, 12),
        getClusters(repositoryId, "function", threshold)
      ]);
      setRanking(nextRanking);
      setClusters(nextClusters);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Similarity analysis failed.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="workspace-view similarity-view animate-in">
      <div className="view-heading split-heading">
        <div>
          <p className="section-kicker"><GitCompareArrows size={14} /> Structural similarity</p>
          <h2>Find code that behaves alike.</h2>
          <p>Weighted Jaccard scoring compares calls, neighbors, callers and imports.</p>
        </div>
        <div className="threshold-control">
          <label htmlFor="cluster-threshold">Cluster threshold</label>
          <div>
            <input
              id="cluster-threshold"
              type="range"
              min="0.2"
              max="0.9"
              step="0.05"
              value={threshold}
              onChange={(event) => setThreshold(Number(event.target.value))}
            />
            <strong>{formatPercent(threshold)}</strong>
          </div>
        </div>
      </div>

      <section className="analysis-control-card">
        <NodePicker
          nodes={functions}
          allowedTypes={["function"]}
          selectedNode={target}
          onSelectNode={(node) => {
            onSelectNode(node);
            setRanking(null);
          }}
          label="Reference function"
          placeholder="Search functions…"
        />
        <button className="primary-button compact" onClick={compare} disabled={!target || loading}>
          {loading ? <LoaderCircle size={17} className="spin" /> : <Play size={16} fill="currentColor" />}
          {loading ? "Comparing graph features…" : "Find similar functions"}
        </button>
      </section>
      {error ? <p className="inline-error">{error}</p> : null}

      {!ranking ? (
        <section className="similarity-empty">
          <div className="comparison-visual">
            <span>Fn</span><i /><span>?</span><i /><span>Fn</span>
          </div>
          <h3>Choose a function to compare its structural fingerprint.</h3>
          <p>Every score includes a feature-by-feature evidence breakdown.</p>
        </section>
      ) : (
        <div className="similarity-layout">
          <section className="surface-card ranking-card">
            <div className="card-heading">
              <div><p className="card-eyebrow">Ranked matches</p><h3>Closest functions</h3></div>
              <span className="count-chip">{ranking.results.length} results</span>
            </div>
            <div className="similarity-results">
              {ranking.results.map((result, index) => {
                const node = nodeMap.get(result.candidateNodeId);
                if (!node) return null;
                return (
                  <button key={result.candidateNodeId} onClick={() => onSelectNode(node)}>
                    <span className="rank-number">{String(index + 1).padStart(2, "0")}</span>
                    <span className="similarity-main">
                      <span><strong>{node.label}</strong><em>{formatPercent(result.score)}</em></span>
                      <span className="score-track"><i style={{ width: formatPercent(result.score) }} /></span>
                      <small>{nodePath(node) ?? node.id}</small>
                      <span className="feature-pills">
                        {Object.entries(result.featureScores)
                          .sort((a, b) => b[1].score - a[1].score)
                          .slice(0, 3)
                          .map(([name, feature]) => (
                            <em key={name} title={feature.matchedFeatures.join(", ")}>
                              {name.replace(/([A-Z])/g, " $1")} {formatPercent(feature.score)}
                            </em>
                          ))}
                      </span>
                    </span>
                  </button>
                );
              })}
            </div>
          </section>

          <section className="surface-card cluster-card">
            <div className="card-heading">
              <div><p className="card-eyebrow">Module patterns</p><h3>Similarity clusters</h3></div>
              <Boxes size={19} />
            </div>
            <p className="card-description">
              Groups where structural similarity meets the {formatPercent(threshold)} threshold.
            </p>
            <div className="cluster-list">
              {(clusters?.clusters ?? []).slice(0, 8).map((cluster, index) => (
                <article key={cluster.clusterId}>
                  <div>
                    <span className={`cluster-swatch cluster-${index % 5}`}>
                      <Sparkles size={15} />
                    </span>
                    <span><strong>Cluster {index + 1}</strong><small>{cluster.links.length} similarity links</small></span>
                    <em>{cluster.memberNodeIds.length}</em>
                  </div>
                  <div className="cluster-members">
                    {cluster.memberNodeIds.slice(0, 5).map((nodeId) => {
                      const node = nodeMap.get(nodeId);
                      return node ? (
                        <button key={nodeId} onClick={() => onSelectNode(node)}>
                          <i style={{ background: nodeMeta(node.type).color }} />{node.label}
                        </button>
                      ) : null;
                    })}
                  </div>
                </article>
              ))}
              {clusters?.clusters.length === 0 ? (
                <p className="empty-copy">No clusters meet this threshold. Lower it and compare again.</p>
              ) : null}
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
