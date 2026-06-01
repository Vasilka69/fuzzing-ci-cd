import { api } from "@/api/client";
import { extractItems, type PageResponse } from "@/shared/types/api";
import type { DeploymentApproval, DeploymentEnvironment, DeploymentRelease } from "@/shared/types/deployment";

export const deploymentsApi = {
  environments: async (query?: { page?: number; size?: number }) =>
    extractItems(await api.get<PageResponse<DeploymentEnvironment> | DeploymentEnvironment[]>("/environments", query)),
  approvals: async (query?: { pipelineRunId?: string; status?: string; page?: number; size?: number }) =>
    extractItems(await api.get<PageResponse<DeploymentApproval> | DeploymentApproval[]>("/deployment-approvals", query)),
  approve: (id: string) => api.post<DeploymentApproval>(`/deployment-approvals/${id}/approve`),
  reject: (id: string) => api.post<DeploymentApproval>(`/deployment-approvals/${id}/reject`),
  releases: async (query?: { environmentId?: string; status?: string; releaseId?: string; page?: number; size?: number }) =>
    extractItems(await api.get<PageResponse<DeploymentRelease> | DeploymentRelease[]>("/deployment-releases", query))
};
