import { IngestionStatus } from "@/components/ingestion-status/ingestion-status";
import { ResultSummary } from "@/components/result-summary/result-summary";

export default async function RepositoryJobPage({
  params
}: {
  params: Promise<{ jobId: string }>;
}) {
  const { jobId } = await params;

  return (
    <main className="page-shell">
      <section className="details-layout">
        <IngestionStatus jobId={jobId} />
        <ResultSummary jobId={jobId} />
      </section>
    </main>
  );
}
