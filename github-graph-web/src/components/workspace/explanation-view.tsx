"use client";

import { useState } from "react";
import {
  ArrowRight,
  Bot,
  CheckCircle2,
  CornerDownLeft,
  LoaderCircle,
  MessageSquareText,
  Send,
  ShieldCheck,
  Sparkles
} from "lucide-react";
import { queryExplanation } from "@/lib/api-client";
import { nodeMeta, nodePath } from "@/lib/graph-utils";
import type { ExplanationResponse, GraphNode, RepositoryGraph } from "@/lib/types";
import { NodePicker } from "./node-picker";

const suggestedQuestions = [
  {
    label: "Explain dependency flow",
    query: "Explain this dependency flow."
  },
  {
    label: "Map failure impact",
    query: "What breaks if this function fails?"
  },
  {
    label: "Investigate an error",
    query: "Why is this error happening?"
  }
];

type ExplanationViewProps = {
  repositoryId: string;
  graph: RepositoryGraph;
  selectedNode: GraphNode | null;
  onSelectNode: (node: GraphNode) => void;
};

export function ExplanationView({
  repositoryId,
  graph,
  selectedNode,
  onSelectNode
}: ExplanationViewProps) {
  const [query, setQuery] = useState("");
  const [stackTrace, setStackTrace] = useState("");
  const [showContext, setShowContext] = useState(false);
  const [response, setResponse] = useState<ExplanationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nodeMap = new Map(graph.nodes.map((node) => [node.id, node]));

  async function ask(question = query) {
    if (!question.trim()) return;
    setQuery(question);
    setLoading(true);
    setError(null);
    try {
      setResponse(
        await queryExplanation({
          repositoryId,
          query: question.trim(),
          targetNodeId: selectedNode?.id ?? null,
          stackTrace: stackTrace.trim() || null,
          errorLog: stackTrace.trim() || null
        })
      );
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to generate an explanation.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="workspace-view explanation-view animate-in">
      <div className="view-heading">
        <p className="section-kicker"><MessageSquareText size={14} /> Grounded explanations</p>
        <h2>Ask the graph, not a guessing machine.</h2>
        <p>Answers are orchestrated from repository evidence and cite the nodes and analyses used.</p>
      </div>

      <div className="explanation-layout">
        <section className="ask-panel">
          <div className="ask-brand">
            <span><Sparkles size={19} /></span>
            <div><strong>Graph Intelligence</strong><small>Evidence-grounded assistant</small></div>
            <em><i /> Ready</em>
          </div>
          <div className="suggested-prompts">
            {suggestedQuestions.map((suggestion) => (
              <button key={suggestion.query} onClick={() => ask(suggestion.query)} disabled={loading}>
                {suggestion.label}<ArrowRight size={14} />
              </button>
            ))}
          </div>
          <NodePicker
            nodes={graph.nodes}
            selectedNode={selectedNode}
            onSelectNode={onSelectNode}
            label="Ground the question on a node (optional)"
          />
          <button className="context-toggle" onClick={() => setShowContext((value) => !value)}>
            <CornerDownLeft size={15} />
            {showContext ? "Hide error context" : "Add stack trace or error context"}
          </button>
          {showContext ? (
            <textarea
              className="code-textarea small"
              value={stackTrace}
              onChange={(event) => setStackTrace(event.target.value)}
              placeholder="Paste an optional stack trace or error log…"
            />
          ) : null}
          <div className="ask-input">
            <textarea
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Ask about structure, dependencies, impact, similarity, cycles or an error…"
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  ask();
                }
              }}
            />
            <button onClick={() => ask()} disabled={!query.trim() || loading} aria-label="Ask question">
              {loading ? <LoaderCircle size={19} className="spin" /> : <Send size={18} />}
            </button>
          </div>
          <p className="ask-hint">Press Enter to ask · Shift + Enter for a new line</p>
          {error ? <p className="inline-error">{error}</p> : null}
        </section>

        <section className="answer-panel">
          {!response && !loading ? (
            <div className="answer-empty">
              <div><Bot size={30} /></div>
              <h3>Answers with an audit trail.</h3>
              <p>Select a suggested question or ask your own. Every substantive answer includes evidence references.</p>
              <span><ShieldCheck size={15} /> Snapshot-scoped · citation checked</span>
            </div>
          ) : null}
          {loading ? (
            <div className="answer-loading">
              <div className="thinking-bars"><i /><i /><i /></div>
              <h3>Gathering graph evidence…</h3>
              <p>Routing intent and running the required Phase 5/6 analyses.</p>
            </div>
          ) : null}
          {response && !loading ? (
            <div className="answer-content">
              <div className="answer-meta">
                <span className={`confidence-badge confidence-${response.confidence}`}>
                  {response.confidence} confidence
                </span>
                <span>{response.intent.replaceAll("_", " ")}</span>
                <em>{response.modelMetadata.model}</em>
              </div>
              <div className="answer-copy">{response.answer}</div>

              {response.referencedNodeIds.length > 0 ? (
                <div className="referenced-nodes">
                  <h4>Referenced graph nodes</h4>
                  <div>
                    {response.referencedNodeIds.slice(0, 8).map((nodeId) => {
                      const node = nodeMap.get(nodeId);
                      return node ? (
                        <button key={nodeId} onClick={() => onSelectNode(node)}>
                          <span style={{ background: nodeMeta(node.type).color }}>
                            {nodeMeta(node.type).short}
                          </span>
                          <span><strong>{node.label}</strong><small>{nodePath(node) ?? node.type}</small></span>
                        </button>
                      ) : null;
                    })}
                  </div>
                </div>
              ) : null}

              <div className="evidence-section">
                <h4><ShieldCheck size={16} /> Supporting evidence</h4>
                {response.supportingEvidence.map((evidence) => (
                  <article key={evidence.evidenceId}>
                    <CheckCircle2 size={16} />
                    <span><strong>{evidence.evidenceId}</strong><small>{evidence.rationale}</small></span>
                    <em>{evidence.sourceType}</em>
                  </article>
                ))}
              </div>

              {response.limitations.length > 0 ? (
                <div className="limitations">
                  <strong>Limitations</strong>
                  {response.limitations.map((limitation) => <p key={limitation}>{limitation}</p>)}
                </div>
              ) : null}

              {response.followUpSuggestions.length > 0 ? (
                <div className="follow-ups">
                  <h4>Continue exploring</h4>
                  {response.followUpSuggestions.map((suggestion) => (
                    <button key={suggestion} onClick={() => ask(suggestion)}>
                      {suggestion}<ArrowRight size={14} />
                    </button>
                  ))}
                </div>
              ) : null}
              <p className="answer-footer">
                Snapshot {response.snapshotMetadata.commitSha?.slice(0, 8)} · Prompt {response.modelMetadata.promptVersion}
              </p>
            </div>
          ) : null}
        </section>
      </div>
    </div>
  );
}
