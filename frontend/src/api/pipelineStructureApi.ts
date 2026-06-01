import { api } from "@/api/client";
import type { DependencySummary, JobSummary, StageSummary } from "@/shared/types/pipeline";

export const pipelineStructureApi = {
  createStage: (
    pipelineId: string,
    payload: { position: number; name: string; description?: string; runPolicy?: string }
  ) => api.post<StageSummary>(`/pipelines/${pipelineId}/stages`, payload),
  createJob: (
    stageId: string,
    payload: {
      jobTemplateId?: string;
      position: number;
      name: string;
      jobType: string;
      params?: Record<string, unknown>;
      script?: string;
      isScriptPrimary?: boolean;
      condition?: string;
      timeoutSeconds?: number;
      maxAttempts?: number;
      resourceLimits?: Record<string, unknown>;
      sandboxPolicy?: Record<string, unknown>;
      continueOnError?: boolean;
    }
  ) => api.post<JobSummary>(`/stages/${stageId}/jobs`, payload),
  addDependency: (jobId: string, payload: { dependsOnJobId: string; condition?: string }) =>
    api.post<DependencySummary>(`/jobs/${jobId}/dependencies`, payload)
};
