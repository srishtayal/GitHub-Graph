"use client";

import { useEffect, useState } from "react";
import {
  getIngestionJob,
  getRepositoryAnalysis,
  getRepositoryFiles,
  getRepositorySummary,
  getRepositorySymbols
} from "@/lib/api-client";
import type {
  IngestionJob,
  RepositoryAnalysis,
  RepositoryFileList,
  RepositorySummary,
  RepositorySymbolList
} from "@/lib/types";

type ResultSummaryProps = {
  jobId: string;
};

export function ResultSummary({ jobId }: ResultSummaryProps) {
  const [job, setJob] = useState<IngestionJob | null>(null);
  const [summary, setSummary] = useState<RepositorySummary | null>(null);
  const [files, setFiles] = useState<RepositoryFileList | null>(null);
  const [symbols, setSymbols] = useState<RepositorySymbolList | null>(null);
  const [analysis, setAnalysis] = useState<RepositoryAnalysis | null>(null);
  const [message, setMessage] = useState("Waiting for ingestion results.");

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const nextJob = await getIngestionJob(jobId);
        if (cancelled) {
          return;
        }
        setJob(nextJob);

        if (nextJob.repositoryId && nextJob.status === "COMPLETED") {
          const [nextSummary, nextFiles, nextSymbols, nextAnalysis] = await Promise.all([
            getRepositorySummary(nextJob.repositoryId),
            getRepositoryFiles(nextJob.repositoryId),
            getRepositorySymbols(nextJob.repositoryId),
            getRepositoryAnalysis(nextJob.repositoryId)
          ]);

          if (!cancelled) {
            setSummary(nextSummary);
            setFiles(nextFiles);
            setSymbols(nextSymbols);
            setAnalysis(nextAnalysis);
            setMessage("Analysis results loaded.");
          }
          return;
        }

        if (!cancelled) {
          setMessage(nextJob.errorMessage ?? `Job status: ${nextJob.status}`);
        }
      } catch {
        if (!cancelled) {
          setMessage("Result lookup is not ready yet.");
        }
      }
    }

    load();
    const intervalId = window.setInterval(load, 2500);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [jobId]);

  return (
    <section className="panel result-stack">
      <p className="eyebrow">Analysis Results</p>
      <p className="hint">{message}</p>
      {summary?.latestSnapshot ? (
        <>
          <div className="metric-grid">
            <div>
              <span className="metric-label">Repository</span>
              <strong>{summary.owner}/{summary.name}</strong>
            </div>
            <div>
              <span className="metric-label">Files</span>
              <strong>{summary.latestSnapshot.totalFiles}</strong>
            </div>
            <div>
              <span className="metric-label">Directories</span>
              <strong>{summary.latestSnapshot.totalDirectories}</strong>
            </div>
            <div>
              <span className="metric-label">Symbols</span>
              <strong>{symbols?.items.length ?? 0}</strong>
            </div>
          </div>
          {analysis ? (
            <div className="phase-card">
              <div>
                <p className="eyebrow">Phase 3 Static Code Extraction</p>
                <p className="hint">
                  Python parser output from the stored structured JSON analysis.
                </p>
              </div>
              <div className="metric-grid phase-metrics">
                <div>
                  <span className="metric-label">Classes</span>
                  <strong>{analysis.summary.totalClasses}</strong>
                </div>
                <div>
                  <span className="metric-label">Functions</span>
                  <strong>{analysis.summary.totalFunctions}</strong>
                </div>
                <div>
                  <span className="metric-label">Method Calls</span>
                  <strong>{analysis.summary.totalMethodCalls}</strong>
                </div>
                <div>
                  <span className="metric-label">API Routes</span>
                  <strong>{analysis.summary.totalApiRoutes}</strong>
                </div>
                <div>
                  <span className="metric-label">Python Files</span>
                  <strong>{analysis.codeFiles.length}</strong>
                </div>
                <div>
                  <span className="metric-label">Dependencies</span>
                  <strong>{analysis.summary.totalModuleDependencies}</strong>
                </div>
              </div>
              {analysis.codeFiles.length === 0 ? (
                <p className="hint">
                  No Python files were found in this repository, so Phase 3 has no Python symbols to show.
                </p>
              ) : null}
              <div className="phase-preview-grid">
                <div className="list-block">
                  <span className="metric-label">Sample Classes</span>
                  <ul>
                    {analysis.classes.slice(0, 6).map((item, index) => (
                      <li key={`${item.relativePath}:${item.qualifiedName}:${item.startLine}:${index}`}>
                        {item.qualifiedName} in {item.relativePath}
                      </li>
                    ))}
                    {analysis.classes.length === 0 ? <li>No classes extracted.</li> : null}
                  </ul>
                </div>
                <div className="list-block">
                  <span className="metric-label">Sample Functions</span>
                  <ul>
                    {analysis.functions.slice(0, 6).map((item, index) => (
                      <li key={`${item.relativePath}:${item.qualifiedName}:${item.startLine}:${index}`}>
                        {item.isAsync ? "async " : ""}
                        {item.qualifiedName}({item.parameters.join(", ")})
                      </li>
                    ))}
                    {analysis.functions.length === 0 ? <li>No functions extracted.</li> : null}
                  </ul>
                </div>
                <div className="list-block">
                  <span className="metric-label">API Routes</span>
                  <ul>
                    {analysis.apiRoutes.slice(0, 6).map((route, index) => (
                      <li key={`${route.relativePath}:${route.httpMethod}:${route.path}:${route.startLine}:${index}`}>
                        {route.httpMethod} {route.path}
                        {" -> "}
                        {route.handler}
                      </li>
                    ))}
                    {analysis.apiRoutes.length === 0 ? <li>No API routes extracted.</li> : null}
                  </ul>
                </div>
                <div className="list-block">
                  <span className="metric-label">Module Dependencies</span>
                  <ul>
                    {analysis.moduleDependencies.slice(0, 6).map((dependency, index) => (
                      <li
                        key={`${dependency.sourcePath}:${dependency.targetModule}:${dependency.resolvedPath ?? "external"}:${index}`}
                      >
                        {dependency.sourcePath}
                        {" -> "}
                        {dependency.resolvedPath ?? dependency.targetModule}
                      </li>
                    ))}
                    {analysis.moduleDependencies.length === 0 ? <li>No module dependencies extracted.</li> : null}
                  </ul>
                </div>
              </div>
            </div>
          ) : null}
          <div className="list-block">
            <span className="metric-label">Languages</span>
            <ul>
              {Object.entries(summary.latestSnapshot.languageSummary).map(([language, count]) => (
                <li key={language}>
                  {language}: {count}
                </li>
              ))}
            </ul>
          </div>
          <div className="list-block">
            <span className="metric-label">Sample Files</span>
            <ul>
              {(files?.items ?? []).slice(0, 8).map((file) => (
                <li key={file.fileId}>
                  {file.relativePath}
                </li>
              ))}
            </ul>
          </div>
        </>
      ) : null}
      {!summary && job && job.status !== "COMPLETED" ? <p className="hint">Still processing.</p> : null}
    </section>
  );
}
