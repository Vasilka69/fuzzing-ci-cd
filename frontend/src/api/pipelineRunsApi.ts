import { api } from "@/api/client";
import { extractItems, type PageResponse } from "@/shared/types/api";
import type { JobExecution, PipelineRun, RunEventsPage } from "@/shared/types/run";

export const pipelineRunsApi = {
  list: async (query?: { pipelineId?: string; status?: string; triggerType?: string; page?: number; size?: number }) =>
    extractItems(await api.get<PageResponse<PipelineRun> | PipelineRun[]>("/pipeline-runs", query)),
  byId: (id: string) => api.get<PipelineRun>(`/pipeline-runs/${id}`),
  graph: (id: string) => api.get<JobExecution[]>(`/pipeline-runs/${id}/graph`),
  events: (id: string, query?: { limit?: number; cursor?: string }) => api.get<RunEventsPage>(`/pipeline-runs/${id}/events`, query),
  cancel: (id: string) => api.post<PipelineRun>(`/pipeline-runs/${id}/cancel`),
  retry: (id: string) => api.post<PipelineRun>(`/pipeline-runs/${id}/retry`)
};
