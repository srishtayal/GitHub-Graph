"use client";

import { useEffect, useState } from "react";
import { getIngestionJob } from "@/lib/api-client";
import type { IngestionJob } from "@/lib/types";

type IngestionStatusProps = {
  jobId: string;
};

export function IngestionStatus({ jobId }: IngestionStatusProps) {
  const [job, setJob] = useState<IngestionJob | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const nextJob = await getIngestionJob(jobId);
        if (!cancelled) {
          setJob(nextJob);
        }
      } catch {
        if (!cancelled) {
          setJob(null);
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
    <section className="panel">
      <p className="eyebrow">Ingestion Job</p>
      <h2>{job?.status ?? "Loading"}</h2>
      <p className="hint">Job ID: {jobId}</p>
      <p className="hint">Repository ID: {job?.repositoryId ?? "Pending"}</p>
      <p className="hint">Started: {job?.startedAt ?? "Not started yet"}</p>
      <p className="hint">Finished: {job?.finishedAt ?? "Still running"}</p>
      {job?.errorMessage ? <p className="hint">Error: {job.errorMessage}</p> : null}
    </section>
  );
}
