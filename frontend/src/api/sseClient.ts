type EventHandlers = {
  onOpen?: () => void;
  onEvent?: (event: MessageEvent<string>) => void;
  onJobEvent?: (payload: unknown) => void;
  onJobLog?: (payload: unknown) => void;
  onError?: (event: Event) => void;
};

export function connectLogStream(
  query: { pipelineRunId?: string; jobId?: string; jobExecutionId?: string },
  handlers: EventHandlers
): EventSource {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";
  const streamUrl = new URL(`${baseUrl}/logs/stream`);
  Object.entries(query).forEach(([key, value]) => {
    if (value) {
      streamUrl.searchParams.set(key, value);
    }
  });
  const source = new EventSource(streamUrl.toString(), { withCredentials: false });

  source.onopen = () => {
    handlers.onOpen?.();
  };

  source.onmessage = (event) => {
    handlers.onEvent?.(event);
  };
  source.addEventListener("job-event", (event) => {
    handlers.onJobEvent?.(safeParse(event));
  });
  source.addEventListener("job-log", (event) => {
    handlers.onJobLog?.(safeParse(event));
  });
  source.onerror = (event) => {
    handlers.onError?.(event);
  };
  return source;
}

function safeParse(event: Event): unknown {
  const payload = (event as MessageEvent<string>).data;
  if (!payload) {
    return null;
  }
  try {
    return JSON.parse(payload);
  } catch {
    return payload;
  }
}
