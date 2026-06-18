type IngestionStatusProps = {
  jobId: string;
};

export function IngestionStatus({ jobId }: IngestionStatusProps) {
  return (
    <section className="panel">
      <p className="eyebrow">Ingestion Job</p>
      <h2>{jobId}</h2>
      <p className="hint">Polling and live status wiring will be added during API integration.</p>
    </section>
  );
}
