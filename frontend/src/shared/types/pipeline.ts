export type PipelineSummary = {
  id: string;
  folderId: string | null;
  name: string;
  description: string | null;
  isActive: boolean;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreatePipelineRequest = {
  folderId?: string | null;
  name: string;
  description?: string | null;
};

export type StageSummary = {
  id: string;
  pipelineId: string;
  position: number;
  name: string;
  description: string | null;
  runPolicy: string;
  createdAt: string;
  updatedAt: string;
};

export type JobSummary = {
  id: string;
  stageId: string;
  jobTemplateId: string | null;
  position: number;
  name: string;
  jobType: string;
  params: Record<string, unknown> | null;
  script: string | null;
  isScriptPrimary: boolean;
  condition: string;
  timeoutSeconds: number;
  maxAttempts: number;
  resourceLimits: Record<string, unknown> | null;
  sandboxPolicy: Record<string, unknown> | null;
  continueOnError: boolean;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
};

export type DependencySummary = {
  id: string;
  jobId: string;
  dependsOnJobId: string;
  condition: string;
  createdAt: string;
};

export type PipelineDetails = {
  pipeline: PipelineSummary;
  stages: StageSummary[];
  jobs: JobSummary[];
  dependencies: DependencySummary[];
};
