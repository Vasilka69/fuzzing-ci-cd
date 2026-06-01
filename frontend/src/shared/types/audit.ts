export type AuditEvent = {
  id: string;
  actorUserId: string | null;
  eventType: string;
  entityType: string | null;
  entityId: string | null;
  payload: Record<string, unknown> | null;
  createdAt: string;
};
