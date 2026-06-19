"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { createIngestion } from "@/lib/api-client";

export function RepoUrlForm() {
  const router = useRouter();
  const [githubUrl, setGithubUrl] = useState("");
  const [message, setMessage] = useState("Ready to accept a public repository URL.");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!githubUrl.trim()) {
      setMessage("Enter a GitHub repository URL to begin.");
      return;
    }

    try {
      const result = await createIngestion(githubUrl.trim());
      setMessage(`Created ingestion job ${result.jobId} with status ${result.status}.`);
      router.push(`/repositories/${result.jobId}`);
    } catch {
      setMessage("Unable to start ingestion. Check the backend response and try again.");
    }
  }

  return (
    <form className="panel form-stack" onSubmit={handleSubmit}>
      <label className="label" htmlFor="github-url">
        Public GitHub Repository URL
      </label>
      <input
        id="github-url"
        className="text-input"
        type="url"
        placeholder="https://github.com/owner/repository"
        value={githubUrl}
        onChange={(event) => setGithubUrl(event.target.value)}
      />
      <button className="primary-button" type="submit">
        Start Ingestion
      </button>
      <p className="hint">{message}</p>
    </form>
  );
}
