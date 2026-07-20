"use client";

import { useEffect, useState } from "react";
import { Clock3, GitCommitHorizontal, LoaderCircle } from "lucide-react";
import { getSnapshotHistory } from "@/lib/api-client";
import type { SnapshotHistory } from "@/lib/types";

export function HistoryView({ repositoryId }: { repositoryId: string }) {
  const [history, setHistory] = useState<SnapshotHistory | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getSnapshotHistory(repositoryId).then(setHistory).catch((reason) => setError(reason instanceof Error ? reason.message : "Unable to load history."));
  }, [repositoryId]);

  return (
    <section className="workspace-section history-view">
      <div className="section-heading"><div><p className="section-kicker"><Clock3 size={14} /> Persistent history</p><h2>Analysis snapshots</h2><p>Each successful scan is retained with its commit and graph.</p></div></div>
      {error ? <p className="inline-error">{error}</p> : null}
      {!history && !error ? <p className="loading-line"><LoaderCircle size={16} className="spin" /> Loading analysis history…</p> : null}
      <div className="snapshot-list">
        {history?.snapshots.map((snapshot) => (
          <article key={snapshot.snapshotId} className="snapshot-card">
            <GitCommitHorizontal size={22} />
            <div><strong>{snapshot.branchName ?? "default"} · {snapshot.commitSha?.slice(0, 8) ?? "unknown"}</strong><p>{snapshot.commitMessage ?? "No commit message"}</p><small>{snapshot.totalFiles} files · {snapshot.totalDirectories} directories · analyzed {new Date(snapshot.analyzedAt).toLocaleString()}</small></div>
          </article>
        ))}
      </div>
    </section>
  );
}
