import { RepoUrlForm } from "@/components/repo-url-form/repo-url-form";

export default function HomePage() {
  return (
    <main className="page-shell">
      <section className="hero">
        <div>
          <p className="eyebrow">GitHub Graph</p>
          <h1>Repository ingestion for structural analysis</h1>
          <p className="lead">
            Submit a public GitHub repository URL to kick off Phase 1 ingestion.
          </p>
        </div>
        <RepoUrlForm />
      </section>
    </main>
  );
}
