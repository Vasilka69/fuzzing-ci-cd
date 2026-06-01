import { api } from "@/api/client";
import { extractItems, type PageResponse } from "@/shared/types/api";
import type { CreatePipelineRequest, PipelineDetails, PipelineSummary } from "@/shared/types/pipeline";
import type { PipelineRun } from "@/shared/types/run";

export const pipelinesApi = {
  list: async (query?: { folderId?: string; isActive?: boolean; query?: string; page?: number; size?: number }) =>
    extractItems(await api.get<PageResponse<PipelineSummary> | PipelineSummary[]>("/pipelines", query)),
  details: (id: string) => api.get<PipelineDetails>(`/pipelines/${id}`),
  create: (payload: CreatePipelineRequest) => api.post<PipelineSummary>("/pipelines", payload),
  run: (pipelineId: string) =>
    api.post<PipelineRun>(`/pipelines/${pipelineId}/runs`, { triggerType: "user", triggerPayload: {} })
};
