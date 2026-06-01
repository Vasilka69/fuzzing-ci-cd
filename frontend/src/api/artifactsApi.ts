import { api } from "@/api/client";
import { extractItems, type PageResponse } from "@/shared/types/api";
import type { Artifact } from "@/shared/types/artifact";

export const artifactsApi = {
  list: async (query?: { pipelineRunId?: string; jobExecutionId?: string; artifactType?: string; page?: number; size?: number }) =>
    extractItems(await api.get<PageResponse<Artifact> | Artifact[]>("/artifacts", query)),
  byId: (id: string) => api.get<Artifact>(`/artifacts/${id}`),
  downloadUrl: (id: string) => api.get<{ downloadUrl: string }>(`/artifacts/${id}/download-url`)
};
