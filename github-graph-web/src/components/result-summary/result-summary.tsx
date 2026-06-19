"use client";

import { useEffect, useState } from "react";
import { getIngestionJob, getRepositoryFiles, getRepositorySummary, getRepositorySymbols } from "@/lib/api-client";
import type { IngestionJob, RepositoryFileList, RepositorySummary, RepositorySymbolList } from "@/lib/types";

type ResultSummaryProps = {
  jobId: string;
};

export function ResultSummary({ jobId }: ResultSummaryProps) {
  const [job, setJob] = useState<IngestionJob | null>(null);
  const [summary, setSummary] = useState<RepositorySummary | null>(null);
  const [files, setFiles] = useState<RepositoryFileList | null>(null);
  const [symbols, setSymbols] = useState<RepositorySymbolList | null>(null);
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
          const [nextSummary, nextFiles, nextSymbols] = await Promise.all([
            getRepositorySummary(nextJob.repositoryId),
            getRepositoryFiles(nextJob.repositoryId),
            getRepositorySymbols(nextJob.repositoryId)
          ]);

          if (!cancelled) {
            setSummary(nextSummary);
            setFiles(nextFiles);
            setSymbols(nextSymbols);
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
