export type ExecutorHeartbeat = {
  id: string;
  workerId: string;
  serviceType: string;
  status: string;
  capacity: Record<string, unknown>;
  lastSeenAt: string;
};
