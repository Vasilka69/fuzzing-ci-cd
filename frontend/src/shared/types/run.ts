export type PipelineRun = {
  id: string;
  pipelineId: string;
  status: string;
  correlationId: string;
  startedBy: string | null;
  triggeredByType: string;
  startedAt: string;
  finishedAt: string | null;
  summary: string | null;
};

export type JobExecution = {
  id: string;
  pipelineRunId: string;
  jobId: string;
  attempt: number;
  status: string;
  workerId: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  errorType: string | null;
  errorCode: string | null;
  errorMessage: string | null;
};

export type RunEventsPage = {
  items: JobExecution[];
  nextCursor: string | null;
};

export type LogLine = {
  id: string;
  message: string;
  ts: string | null;
};

export type LogPage = {
  items: LogLine[];
  nextCursor: string | null;
};
