export type DeploymentEnvironment = {
  id: string;
  name: string;
  description: string | null;
  config: Record<string, unknown> | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
};

export type DeploymentApproval = {
  id: string;
  pipelineRunId: string | null;
  jobExecutionId: string | null;
  environmentId: string;
  requestedBy: string | null;
  approvedBy: string | null;
  status: string;
  reason: string | null;
  createdAt: string;
  decidedAt: string | null;
};

export type DeploymentRelease = {
  id: string;
  releaseId: string;
  pipelineRunId: string | null;
  jobExecutionId: string | null;
  environmentId: string | null;
  targetConnectionId: string | null;
  artifactUri: string;
  artifactSha256: string | null;
  deploymentType: string;
  status: string;
  manifestUri: string | null;
  rollbackReleaseId: string | null;
  healthcheckResult: Record<string, unknown> | null;
  metadata: Record<string, unknown> | null;
  createdAt: string;
  deployedAt: string | null;
};
