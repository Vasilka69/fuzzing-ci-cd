import { api } from "@/api/client";
import { extractItems, type PageResponse } from "@/shared/types/api";
import type { CreateExternalConnectionRequest, ExternalConnection } from "@/shared/types/security";

export const connectionsApi = {
  list: async (query?: { connectionType?: string; page?: number; size?: number }) =>
    extractItems(await api.get<PageResponse<ExternalConnection> | ExternalConnection[]>("/external-connections", query)),
  create: (payload: CreateExternalConnectionRequest) => api.post<ExternalConnection>("/external-connections", payload)
};
