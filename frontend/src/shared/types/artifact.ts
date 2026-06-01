export type Artifact = {
  id: string;
  pipelineRunId: string | null;
  jobExecutionId: string | null;
  artifactType: string;
  name: string;
  uri: string;
  sha256: string | null;
  sizeBytes: number | null;
  contentType: string | null;
  retentionPolicy: string;
  metadata: Record<string, unknown> | null;
  createdAt: string;
  expiresAt: string | null;
  status: string;
  checksumVerified: boolean;
};
