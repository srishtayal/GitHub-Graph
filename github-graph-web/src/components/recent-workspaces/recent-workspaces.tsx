"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowUpRight, Clock3, GitBranch } from "lucide-react";

type RecentWorkspace = {
  jobId: string;
  repositoryId: string;
  owner: string;
  name: string;
  analyzedAt: string;
};

export function RecentWorkspaces() {
  const [recent, setRecent] = useState<RecentWorkspace[]>([]);

  useEffect(() => {
    try {
      setRecent(JSON.parse(window.localStorage.getItem("github-graph-recent") ?? "[]"));
    } catch {
      setRecent([]);
    }
  }, []);

  if (recent.length === 0) return null;

  return (
    <section className="recent-section">
      <div className="recent-heading">
        <div>
          <p className="section-kicker"><Clock3 size={14} /> Local history</p>
          <h2>Continue exploring.</h2>
        </div>
        <span>Stored in this browser</span>
      </div>
      <div className="recent-grid">
        {recent.map((item, index) => (
          <Link key={item.jobId} href={`/repositories/${item.jobId}`}>
            <span className={`recent-icon recent-${index % 4}`}><GitBranch size={19} /></span>
            <span><strong>{item.owner}/{item.name}</strong><small>Analyzed {new Date(item.analyzedAt).toLocaleDateString()}</small></span>
            <ArrowUpRight size={17} />
          </Link>
        ))}
      </div>
    </section>
  );
}
