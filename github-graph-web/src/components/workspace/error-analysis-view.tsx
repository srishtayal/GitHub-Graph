"use client";

import { useMemo, useState } from "react";
import {
  AlertTriangle,
  Check,
  CheckCircle2,
  ChevronRight,
  Clock3,
  FileWarning,
  History,
  LoaderCircle,
  Save,
  ScanSearch
} from "lucide-react";
import {
  confirmFailure,
  createFailure,
  localizeFailure
} from "@/lib/api-client";
import { formatPercent, nodeMeta, nodePath } from "@/lib/graph-utils";
import type {
  BugLocalizationResult,
  FailureRecord,
  GraphNode,
  RepositoryGraph
} from "@/lib/types";
import { NodePicker } from "./node-picker";

type ErrorAnalysisViewProps = {
  repositoryId: string;
  snapshotId: string;
  graph: RepositoryGraph;
  selectedNode: GraphNode | null;
  onSelectNode: (node: GraphNode) => void;
  failures: FailureRecord[];
  onFailureSaved: (failure: FailureRecord) => void;
};

const localizationConfiguration = {
  maxTraversalDepth: 2,
  maxPastFailures: 10,
  maxSuspectedRootCauses: 10
};

export function ErrorAnalysisView({
  repositoryId,
  snapshotId,
  graph,
  selectedNode,
  onSelectNode,
  failures,
  onFailureSaved
}: ErrorAnalysisViewProps) {
  const functions = graph.nodes.filter((node) => node.type === "function");
  const target = selectedNode ?? functions[0] ?? null;
  const [stackTrace, setStackTrace] = useState("");
  const [errorLog, setErrorLog] = useState("");
  const [result, setResult] = useState<BugLocalizationResult | null>(null);
  const [savedFailure, setSavedFailure] = useState<FailureRecord | null>(null);
  const [loading, setLoading] = useState<"localize" | "save" | "confirm" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const nodeMap = useMemo(
    () => new Map(graph.nodes.map((node) => [node.id, node])),
    [graph.nodes]
  );

  const evidenceReady = Boolean(target || stackTrace.trim() || errorLog.trim());

  function requestPayload() {
    return {
      repositoryId,
      snapshotId,
      failingNodeId: target?.id ?? null,
      errorLog: errorLog.trim() || null,
      stackTrace: stackTrace.trim() || null,
      failurePathNodeIds: [],
      configuration: localizationConfiguration
    };
  }

  async function localize() {
    setLoading("localize");
    setError(null);
    setSavedFailure(null);
    try {
      setResult(await localizeFailure(requestPayload()));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Failure localization failed.");
    } finally {
      setLoading(null);
    }
  }

  async function saveAnalysis() {
    if (!result) return;
    setLoading("save");
    setError(null);
    try {
      const failure = await createFailure(repositoryId, {
        snapshotId,
        failingNodeId: target?.id ?? null,
        errorLog: errorLog.trim() || null,
        stackTrace: stackTrace.trim() || null,
        failurePathNodeIds: result.resolvedFailurePath.nodeIds,
        occurredAt: new Date().toISOString(),
        localizationConfiguration
      });
      setSavedFailure(failure);
      onFailureSaved(failure);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to save this analysis.");
    } finally {
      setLoading(null);
    }
  }

  async function confirmCandidate(nodeId: string) {
    if (!savedFailure) return;
    setLoading("confirm");
    setError(null);
    try {
      const updated = await confirmFailure(
        savedFailure.failureId,
        nodeId,
        "Confirmed from the Phase 8 error analysis workspace."
      );
      setSavedFailure(updated);
      onFailureSaved(updated);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to confirm this root cause.");
    } finally {
      setLoading(null);
    }
  }

  return (
    <div className="workspace-view error-view animate-in">
      <div className="view-heading split-heading">
        <div>
          <p className="section-kicker"><ScanSearch size={14} /> Bug localization</p>
          <h2>Turn a stack trace into ranked suspects.</h2>
          <p>Graph paths, stack frames, history and criticality combine into explainable scores.</p>
        </div>
        <div className="history-stat">
          <History size={18} />
          <span><strong>{failures.length}</strong><small>recorded failures</small></span>
        </div>
      </div>

      <div className="error-layout">
        <section className="surface-card failure-input-card">
          <div className="card-heading">
            <div><p className="card-eyebrow">Failure evidence</p><h3>What went wrong?</h3></div>
            <FileWarning size={20} />
          </div>
          <NodePicker
            nodes={graph.nodes}
            selectedNode={target}
            onSelectNode={(node) => {
              onSelectNode(node);
              setResult(null);
              setSavedFailure(null);
            }}
            label="Likely failing node (optional)"
          />
          <label className="field-label" htmlFor="error-log">Error message or log</label>
          <textarea
            id="error-log"
            className="code-textarea small"
            value={errorLog}
            onChange={(event) => setErrorLog(event.target.value)}
            placeholder="RuntimeError: connection pool exhausted"
          />
          <label className="field-label" htmlFor="stack-trace">Stack trace</label>
          <textarea
            id="stack-trace"
            className="code-textarea"
            value={stackTrace}
            onChange={(event) => setStackTrace(event.target.value)}
            placeholder={'File "app/service.py", line 42, in execute\n  return database.query()\nRuntimeError: connection pool exhausted'}
          />
          <button
            className="primary-button compact full-width"
            onClick={localize}
            disabled={!evidenceReady || Boolean(loading)}
          >
            {loading === "localize" ? <LoaderCircle size={17} className="spin" /> : <ScanSearch size={17} />}
            {loading === "localize" ? "Resolving evidence…" : "Find likely root causes"}
          </button>
          {error ? <p className="inline-error">{error}</p> : null}
        </section>

        <section className="localization-panel">
          {!result ? (
            <div className="error-empty">
              <div className="radar-visual"><i /><i /><span><AlertTriangle size={22} /></span></div>
              <h3>Evidence will resolve here.</h3>
              <p>Frames are matched to graph nodes before candidates are ranked.</p>
            </div>
          ) : (
            <>
              <div className="localization-summary">
                <div>
                  <p className="card-eyebrow">Resolution summary</p>
                  <h3>{result.suspectedRootCauses.length} root-cause candidates</h3>
                </div>
                <div className="resolution-chips">
                  <span className="success"><Check size={13} />{result.resolvedFailurePath.stackFrameNodeIds.length} frames resolved</span>
                  <span>{result.impactedNodeIds.length} impacted nodes</span>
                  {result.resolvedFailurePath.unresolvedReferences.length > 0 ? (
                    <span className="warning">
                      {result.resolvedFailurePath.unresolvedReferences.length} unresolved
                    </span>
                  ) : null}
                </div>
              </div>

              <div className="candidate-list">
                {result.suspectedRootCauses.map((candidate, index) => {
                  const node = nodeMap.get(candidate.nodeId);
                  const confirmed = savedFailure?.confirmedRootCauseNodeIds.includes(candidate.nodeId);
                  return (
                    <article
                      key={candidate.nodeId}
                      className={confirmed ? "is-confirmed" : ""}
                      data-testid={`candidate-${candidate.nodeId}`}
                    >
                      <div className="candidate-rank">{String(index + 1).padStart(2, "0")}</div>
                      <div className="candidate-body">
                        <div className="candidate-heading">
                          <span
                            className="node-type-mark"
                            style={{ background: nodeMeta(node?.type ?? "function").color }}
                          >
                            {nodeMeta(node?.type ?? "function").short}
                          </span>
                          <span>
                            <strong>{node?.label ?? candidate.nodeId}</strong>
                            <small>{node ? nodePath(node) ?? node.type : candidate.nodeId}</small>
                          </span>
                          <span className={`confidence-badge confidence-${candidate.confidence.toLowerCase()}`}>
                            {candidate.confidence}
                          </span>
                          <em>{formatPercent(candidate.score)}</em>
                        </div>
                        <div className="score-track large"><i style={{ width: formatPercent(candidate.score) }} /></div>
                        <div className="reason-breakdown">
                          {candidate.reasons.map((reason) => (
                            <span key={`${candidate.nodeId}:${reason.kind}`} title={reason.detail ?? reason.kind}>
                              <i style={{ width: formatPercent(reason.weight) }} />
                              <strong>{reason.kind.replaceAll("_", " ")}</strong>
                              <em>{formatPercent(reason.weight)}</em>
                            </span>
                          ))}
                        </div>
                        <div className="candidate-actions">
                          {node ? <button onClick={() => onSelectNode(node)}>Inspect node <ChevronRight size={14} /></button> : null}
                          {savedFailure && !confirmed ? (
                            <button
                              className="confirm-button"
                              onClick={() => confirmCandidate(candidate.nodeId)}
                              disabled={loading === "confirm"}
                            >
                              <CheckCircle2 size={15} /> Confirm root cause
                            </button>
                          ) : null}
                          {confirmed ? <span className="confirmed-label"><CheckCircle2 size={15} /> Confirmed root cause</span> : null}
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>

              {!savedFailure ? (
                <button className="secondary-button save-failure" onClick={saveAnalysis} disabled={Boolean(loading)}>
                  {loading === "save" ? <LoaderCircle size={16} className="spin" /> : <Save size={16} />}
                  Save analysis to failure history
                </button>
              ) : (
                <p className="saved-callout">
                  <CheckCircle2 size={16} />
                  Failure {savedFailure.failureId.slice(0, 8)} saved. Confirm a candidate to improve future localization.
                </p>
              )}
            </>
          )}
        </section>
      </div>

      {failures.length > 0 ? (
        <section className="failure-history surface-card">
          <div className="card-heading">
            <div><p className="card-eyebrow">Persistent learning</p><h3>Failure history</h3></div>
            <History size={19} />
          </div>
          <div>
            {failures.slice(0, 6).map((failure) => (
              <article key={failure.failureId}>
                <span className={`history-status status-${failure.status.toLowerCase()}`} />
                <span>
                  <strong>{failure.errorSignature.exceptionType ?? "Recorded failure"}</strong>
                  <small>{failure.failureId.slice(0, 8)} · {failure.status}</small>
                </span>
                <span>
                  <Clock3 size={13} />
                  {new Date(failure.occurredAt).toLocaleDateString()}
                </span>
                <em>{failure.confirmedRootCauseNodeIds.length} confirmed causes</em>
              </article>
            ))}
          </div>
        </section>
      ) : null}
    </div>
  );
}
