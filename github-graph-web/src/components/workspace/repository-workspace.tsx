"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  AlertTriangle,
  ArrowLeft,
  Boxes,
  BrainCircuit,
  CheckCircle2,
  CircleDot,
  Download,
  GitBranch,
  GitFork,
  LayoutDashboard,
  History,
  LoaderCircle,
  Menu,
  MessageSquareText,
  Network,
  RefreshCw,
  SearchCode,
  X
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { getIngestionJob, getRepositoryWorkspace } from "@/lib/api-client";
import { downloadReport, retryIngestion } from "@/lib/api-client";
import type {
  FailureRecord,
  GraphNode,
  IngestionJob,
  RepositoryWorkspaceData
} from "@/lib/types";
import { DependencyView } from "./dependency-view";
import { ErrorAnalysisView } from "./error-analysis-view";
import { ExplanationView } from "./explanation-view";
import { GraphExplorer } from "./graph-explorer";
import { OverviewDashboard } from "./overview-dashboard";
import { SimilarityView } from "./similarity-view";
import { HistoryView } from "./history-view";

type WorkspaceTab = "overview" | "graph" | "dependencies" | "similarity" | "errors" | "history" | "ask";

const navigation: Array<{
  id: WorkspaceTab;
  label: string;
  icon: LucideIcon;
}> = [
  { id: "overview", label: "Overview", icon: LayoutDashboard },
  { id: "graph", label: "Graph explorer", icon: Network },
  { id: "dependencies", label: "Dependencies", icon: GitBranch },
  { id: "similarity", label: "Similarity", icon: Boxes },
  { id: "errors", label: "Error analysis", icon: SearchCode },
  { id: "history", label: "Analysis history", icon: History },
  { id: "ask", label: "Ask the graph", icon: MessageSquareText }
];

type RepositoryWorkspaceProps = {
  jobId: string;
};

export function RepositoryWorkspace({ jobId }: RepositoryWorkspaceProps) {
  const router = useRouter();
  const [job, setJob] = useState<IngestionJob | null>(null);
  const [data, setData] = useState<RepositoryWorkspaceData | null>(null);
  const [activeTab, setActiveTab] = useState<WorkspaceTab>("overview");
  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [message, setMessage] = useState("Connecting to the ingestion pipeline…");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [exporting, setExporting] = useState<"json" | "pdf" | null>(null);

  useEffect(() => {
    let cancelled = false;
    let timeoutId: number | undefined;

    async function load() {
      try {
        const nextJob = await getIngestionJob(jobId);
        if (cancelled) return;
        setJob(nextJob);

        if (nextJob.status === "COMPLETED" && nextJob.repositoryId) {
          setMessage("Loading repository intelligence…");
          const workspace = await getRepositoryWorkspace(nextJob.repositoryId);
          if (cancelled) return;
          setData(workspace);
          setLoadError(null);
          setMessage("Repository intelligence ready.");
          const recent = {
            jobId,
            repositoryId: workspace.summary.repositoryId,
            owner: workspace.summary.owner,
            name: workspace.summary.name,
            analyzedAt: new Date().toISOString()
          };
          const current = JSON.parse(
            window.localStorage.getItem("github-graph-recent") ?? "[]"
          ) as Array<typeof recent>;
          window.localStorage.setItem(
            "github-graph-recent",
            JSON.stringify([recent, ...current.filter((item) => item.jobId !== jobId)].slice(0, 4))
          );
          return;
        }

        if (nextJob.status === "FAILED") {
          setLoadError(
            nextJob.errorMessage ??
              "Repository analysis failed. Review the error category and try another repository."
          );
          setMessage(nextJob.errorCategory ?? "Analysis failed");
          return;
        }

        setMessage(statusMessage(nextJob.status));
        timeoutId = window.setTimeout(load, 2200);
      } catch (reason) {
        if (cancelled) return;
        setLoadError(reason instanceof Error ? reason.message : "Unable to load this analysis.");
        timeoutId = window.setTimeout(load, 4000);
      }
    }

    load();
    return () => {
      cancelled = true;
      if (timeoutId) window.clearTimeout(timeoutId);
    };
  }, [jobId]);

  function navigate(tab: string) {
    setActiveTab(tab as WorkspaceTab);
    setMobileNavOpen(false);
  }

  function inspectNode(node: GraphNode) {
    setSelectedNode(node);
    setActiveTab("graph");
  }

  function updateFailure(failure: FailureRecord) {
    setData((current) => {
      if (!current) return current;
      const withoutCurrent = current.failures.failures.filter(
        (item) => item.failureId !== failure.failureId
      );
      return {
        ...current,
        failures: {
          ...current.failures,
          failures: [failure, ...withoutCurrent]
        }
      };
    });
  }

  async function retryFailedJob() {
    setRetrying(true);
    try {
      const retry = await retryIngestion(jobId);
      router.replace(`/repositories/${retry.jobId}`);
    } finally {
      setRetrying(false);
    }
  }

  async function exportReport(format: "json" | "pdf") {
    setExporting(format);
    try { await downloadReport(data!.summary.repositoryId, format); }
    finally { setExporting(null); }
  }

  if (!data) {
    return (
      <main className="pipeline-page">
        <div className="pipeline-grid" />
        <Link href="/" className="back-link"><ArrowLeft size={16} /> New repository</Link>
        <section className="pipeline-card">
          <div className={`pipeline-orb ${job?.status === "FAILED" ? "failed" : ""}`}>
            {job?.status === "FAILED" ? (
              <AlertTriangle size={30} />
            ) : (
              <BrainCircuit size={30} />
            )}
            <i /><i />
          </div>
          <p className="section-kicker">
            {job?.status === "FAILED" ? <AlertTriangle size={14} /> : <CircleDot size={14} />}
            Ingestion pipeline
          </p>
          <h1>{message}</h1>
          <p className="pipeline-description">
            {loadError ??
              "We’re cloning, indexing, parsing and constructing the code graph. This page updates automatically."}
          </p>
          <div className="pipeline-steps">
            {["Validate repository", "Clone snapshot", "Extract Python code", "Persist graph"].map(
              (step, index) => {
                const completed = statusProgress(job?.status) > index;
                const active = statusProgress(job?.status) === index;
                return (
                  <div key={step} className={completed ? "complete" : active ? "active" : ""}>
                    <span>{completed ? <CheckCircle2 size={17} /> : active ? <LoaderCircle size={17} className="spin" /> : index + 1}</span>
                    <strong>{step}</strong>
                  </div>
                );
              }
            )}
          </div>
          <div className="job-reference">
            <span>Job reference</span><code>{jobId}</code>
            <span>Status</span><strong>{job?.status ?? "CONNECTING"}</strong>
          </div>
          {loadError ? (
            <button className="secondary-button" onClick={job?.status === "FAILED" ? retryFailedJob : () => window.location.reload()} disabled={retrying}>
              <RefreshCw size={16} /> {job?.status === "FAILED" ? (retrying ? "Retrying…" : "Retry analysis") : "Retry connection"}
            </button>
          ) : null}
        </section>
      </main>
    );
  }

  const snapshot = data.summary.latestSnapshot;

  return (
    <main className="workspace-shell">
      <aside className={`workspace-sidebar ${mobileNavOpen ? "is-open" : ""}`}>
        <div className="sidebar-brand">
          <Link href="/" aria-label="GitHub Graph home">
            <span><GitBranch size={19} /></span>
            <strong>GitHub<br />Graph</strong>
          </Link>
          <button onClick={() => setMobileNavOpen(false)} aria-label="Close navigation">
            <X size={19} />
          </button>
        </div>
        <nav aria-label="Repository workspace">
          <p>Workspace</p>
          {navigation.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                className={activeTab === item.id ? "is-active" : ""}
                onClick={() => navigate(item.id)}
              >
                <Icon size={18} />
                <span>{item.label}</span>
                {item.id === "errors" && data.failures.failures.length > 0 ? (
                  <em>{data.failures.failures.length}</em>
                ) : null}
              </button>
            );
          })}
        </nav>
        <div className="sidebar-repo">
          <span><GitFork size={17} /></span>
          <div>
            <strong>{data.summary.owner}/{data.summary.name}</strong>
            <small>{snapshot?.branchName ?? "default"} · {snapshot?.commitSha.slice(0, 7)}</small>
          </div>
        </div>
        <div className="sidebar-footer">
          <span><i /> Analysis healthy</span>
          <small>Snapshot scoped</small>
        </div>
      </aside>

      {mobileNavOpen ? <button className="nav-scrim" onClick={() => setMobileNavOpen(false)} /> : null}

      <section className="workspace-main">
        <header className="workspace-topbar">
          <button className="mobile-menu" onClick={() => setMobileNavOpen(true)} aria-label="Open navigation">
            <Menu size={20} />
          </button>
          <div className="breadcrumb">
            <span>Repositories</span><em>/</em><strong>{data.summary.name}</strong>
          </div>
          <div className="topbar-status">
            <span><i /> Synced</span>
            <button className="export-button" onClick={() => exportReport("json")} disabled={exporting !== null}><Download size={15} /> {exporting === "json" ? "Exporting…" : "JSON"}</button>
            <button className="export-button" onClick={() => exportReport("pdf")} disabled={exporting !== null}><Download size={15} /> {exporting === "pdf" ? "Exporting…" : "PDF"}</button>
            <a href={data.summary.githubUrl} target="_blank" rel="noreferrer">
              <GitFork size={16} /> View source
            </a>
          </div>
        </header>

        {activeTab === "overview" ? (
          <OverviewDashboard data={data} onNavigate={navigate} onSelectNode={inspectNode} />
        ) : null}
        {activeTab === "graph" ? (
          <GraphExplorer
            repositoryId={data.summary.repositoryId}
            repositoryName={data.summary.name}
            graph={data.graph}
            critical={data.critical}
            selectedNode={selectedNode}
            onSelectNode={setSelectedNode}
          />
        ) : null}
        {activeTab === "dependencies" ? (
          <DependencyView
            repositoryId={data.summary.repositoryId}
            graph={data.graph}
            selectedNode={selectedNode}
            onSelectNode={setSelectedNode}
            onOpenGraph={() => navigate("graph")}
          />
        ) : null}
        {activeTab === "similarity" ? (
          <SimilarityView
            repositoryId={data.summary.repositoryId}
            graph={data.graph}
            selectedNode={selectedNode}
            onSelectNode={setSelectedNode}
          />
        ) : null}
        {activeTab === "errors" && snapshot ? (
          <ErrorAnalysisView
            repositoryId={data.summary.repositoryId}
            snapshotId={snapshot.snapshotId}
            graph={data.graph}
            selectedNode={selectedNode}
            onSelectNode={setSelectedNode}
            failures={data.failures.failures}
            onFailureSaved={updateFailure}
          />
        ) : null}
        {activeTab === "history" ? <HistoryView repositoryId={data.summary.repositoryId} /> : null}
        {activeTab === "ask" ? (
          <ExplanationView
            repositoryId={data.summary.repositoryId}
            graph={data.graph}
            selectedNode={selectedNode}
            onSelectNode={setSelectedNode}
          />
        ) : null}
      </section>
    </main>
  );
}

function statusProgress(status?: string): number {
  const progress: Record<string, number> = {
    QUEUED: 0,
    VALIDATING: 0,
    CLONING: 1,
    INDEXING: 2,
    ANALYZING: 2,
    PERSISTING: 3,
    COMPLETED: 4
  };
  return progress[status ?? ""] ?? 0;
}

function statusMessage(status: string): string {
  const messages: Record<string, string> = {
    QUEUED: "Your repository is queued.",
    VALIDATING: "Validating public repository access…",
    CLONING: "Cloning a bounded repository snapshot…",
    INDEXING: "Indexing files, symbols and metadata…",
    ANALYZING: "Extracting Python structure and relationships…",
    PERSISTING: "Building and persisting the code graph…"
  };
  return messages[status] ?? `Analysis is ${status.toLowerCase()}…`;
}
