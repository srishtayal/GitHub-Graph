import { env } from "./env";
import type { CreateIngestionResponse } from "./types";

export async function createIngestion(githubUrl: string): Promise<CreateIngestionResponse> {
  const response = await fetch(`${env.apiBaseUrl}/api/v1/repositories/ingestions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ githubUrl })
  });

  if (!response.ok) {
    throw new Error("Unable to create ingestion job");
  }

  return response.json();
}
