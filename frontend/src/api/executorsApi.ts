import { api } from "@/api/client";
import { extractItems, type PageResponse } from "@/shared/types/api";
import type { ExecutorHeartbeat } from "@/shared/types/executor";

export const executorsApi = {
  list: async (query?: { page?: number; size?: number }) =>
    extractItems(await api.get<PageResponse<ExecutorHeartbeat> | ExecutorHeartbeat[]>("/executors", query))
};
