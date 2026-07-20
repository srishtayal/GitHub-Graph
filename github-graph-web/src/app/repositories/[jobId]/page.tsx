import { RepositoryWorkspace } from "@/components/workspace/repository-workspace";

export default async function RepositoryJobPage({
  params
}: {
  params: Promise<{ jobId: string }>;
}) {
  const { jobId } = await params;

  return <RepositoryWorkspace jobId={jobId} />;
}
