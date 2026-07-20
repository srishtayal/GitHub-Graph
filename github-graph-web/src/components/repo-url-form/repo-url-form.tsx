"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, CheckCircle2, GitFork, LoaderCircle, LockKeyhole } from "lucide-react";
import { createIngestion } from "@/lib/api-client";

const samples = [
  { label: "itsdangerous", url: "https://github.com/pallets/itsdangerous" },
  { label: "flask", url: "https://github.com/pallets/flask" }
];

export function RepoUrlForm() {
  const router = useRouter();
  const [githubUrl, setGithubUrl] = useState("");
  const [message, setMessage] = useState("Public Python repositories are supported.");
  const [submitting, setSubmitting] = useState(false);
  const [hasError, setHasError] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!githubUrl.trim()) {
      setHasError(true);
      setMessage("Enter a GitHub repository URL to begin.");
      return;
    }

    setSubmitting(true);
    setHasError(false);
    setMessage("Validating repository access…");
    try {
      const result = await createIngestion(githubUrl.trim());
      setMessage(`Ingestion ${result.status.toLowerCase()}. Opening workspace…`);
      router.push(`/repositories/${result.jobId}`);
    } catch (reason) {
      setHasError(true);
      setMessage(
        reason instanceof Error
          ? reason.message
          : "Unable to start ingestion. Check the URL and try again."
      );
      setSubmitting(false);
    }
  }

  return (
    <form className="repo-form" onSubmit={handleSubmit}>
      <div className="repo-form-heading">
        <span><GitFork size={20} /></span>
        <div><strong>Analyze a repository</strong><small>No setup. Paste and explore.</small></div>
      </div>
      <label htmlFor="github-url">GitHub repository URL</label>
      <div className={`url-control ${hasError ? "has-error" : ""}`}>
        <GitFork size={18} />
        <input
          id="github-url"
          type="url"
          placeholder="https://github.com/owner/repository"
          value={githubUrl}
          onChange={(event) => {
            setGithubUrl(event.target.value);
            setHasError(false);
          }}
          disabled={submitting}
        />
        <button type="submit" disabled={submitting}>
          {submitting ? <LoaderCircle size={18} className="spin" /> : <ArrowRight size={18} />}
          <span>{submitting ? "Starting…" : "Build graph"}</span>
        </button>
      </div>
      <div className="form-meta">
        <p className={hasError ? "error" : ""}>
          {hasError ? <span>!</span> : <CheckCircle2 size={14} />}
          {message}
        </p>
        <span><LockKeyhole size={13} /> Public repos only</span>
      </div>
      <div className="sample-repos">
        <span>Try a sample:</span>
        {samples.map((sample) => (
          <button key={sample.url} type="button" onClick={() => setGithubUrl(sample.url)}>
            {sample.label}
          </button>
        ))}
      </div>
    </form>
  );
}
