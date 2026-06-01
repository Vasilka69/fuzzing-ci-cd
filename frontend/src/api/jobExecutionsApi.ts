import { api } from "@/api/client";
import { extractItems, type PageResponse } from "@/shared/types/api";
import type { JobExecution, LogPage } from "@/shared/types/run";

export const jobExecutionsApi = {
  listByRun: async (pipelineRunId: string) =>
    extractItems(await api.get<PageResponse<JobExecution> | JobExecution[]>("/job-executions", { pipelineRunId })),
  logs: (id: string, query?: { cursor?: string; limit?: number; tail?: number }) =>
    api.get<LogPage>(`/job-executions/${id}/logs`, query)
};
