"use client";

import { FormEvent, useState } from "react";
import { createIngestion } from "@/lib/api-client";

export function RepoUrlForm() {
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
    } catch {
      setMessage("Backend wiring comes next. The form contract is in place.");
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
