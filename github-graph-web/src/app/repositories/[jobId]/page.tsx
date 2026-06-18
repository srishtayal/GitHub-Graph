import { IngestionStatus } from "@/components/ingestion-status/ingestion-status";
import { MetadataSummary } from "@/components/metadata-summary/metadata-summary";

export default function RepositoryJobPage({
  params
}: {
  params: { jobId: string };
}) {
  return (
    <main className="page-shell">
      <section className="details-layout">
        <IngestionStatus jobId={params.jobId} />
        <MetadataSummary />
      </section>
    </main>
  );
}
