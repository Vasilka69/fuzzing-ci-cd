import { api } from "@/api/client";
import { extractItems, type PageResponse } from "@/shared/types/api";
import type { CreateSecretRefRequest, SecretRef } from "@/shared/types/security";

export const secretRefsApi = {
  list: async (query?: { page?: number; size?: number }) =>
    extractItems(await api.get<PageResponse<SecretRef> | SecretRef[]>("/secret-refs", query)),
  create: (payload: CreateSecretRefRequest) => api.post<SecretRef>("/secret-refs", payload)
};
