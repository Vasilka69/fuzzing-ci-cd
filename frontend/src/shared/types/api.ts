export type ApiErrorPayload = {
  error?: {
    code?: string;
    message?: string;
    details?: unknown;
  };
  correlationId?: string;
};

export type PageInfo = {
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
};

export type PageResponse<T> = {
  items: T[];
  pageInfo?: PageInfo;
};

export function extractItems<T>(payload: T[] | PageResponse<T> | null | undefined): T[] {
  if (Array.isArray(payload)) {
    return payload;
  }
  if (payload && Array.isArray(payload.items)) {
    return payload.items;
  }
  return [];
}

export class ApiError extends Error {
  readonly code: string;
  readonly correlationId?: string;
  readonly status: number;
  readonly details?: unknown;

  constructor(message: string, status: number, code = "api_error", correlationId?: string, details?: unknown) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.correlationId = correlationId;
    this.status = status;
    this.details = details;
  }
}
