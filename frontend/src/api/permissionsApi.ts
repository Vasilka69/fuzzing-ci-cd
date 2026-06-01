import { api } from "@/api/client";
import { extractItems, type PageResponse } from "@/shared/types/api";
import type { CreatePermissionAssignmentRequest, PermissionAssignment } from "@/shared/types/security";

export const permissionsApi = {
  list: async (query?: { resourceType?: string; resourceId?: string; page?: number; size?: number }) =>
    extractItems(await api.get<PageResponse<PermissionAssignment> | PermissionAssignment[]>("/permissions", query)),
  create: (payload: CreatePermissionAssignmentRequest) => api.post<PermissionAssignment>("/permissions", payload),
  remove: (id: string) => api.delete<void>(`/permissions/${id}`)
};
