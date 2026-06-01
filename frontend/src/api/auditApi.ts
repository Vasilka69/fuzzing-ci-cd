import { api } from "@/api/client";
import { extractItems, type PageResponse } from "@/shared/types/api";
import type { AuditEvent } from "@/shared/types/audit";

export const auditApi = {
  list: async (query?: { from?: string; to?: string; actorId?: string; resourceType?: string; page?: number; size?: number }) =>
    extractItems(await api.get<PageResponse<AuditEvent> | AuditEvent[]>("/audit-events", query))
};
