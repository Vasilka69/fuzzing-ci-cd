import { ApiError, type ApiErrorPayload } from "@/shared/types/api";
import type { AuthSession } from "@/shared/types/auth";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";
const AUTH_STORAGE_KEY = "cicd.master.auth";

type QueryParams = Record<string, string | number | boolean | null | undefined>;

function buildUrl(path: string, query?: QueryParams): string {
  const url = new URL(path.startsWith("http") ? path : `${API_BASE_URL}${path}`);
  if (!query) {
    return url.toString();
  }
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    url.searchParams.set(key, String(value));
  });
  return url.toString();
}

export function readAuthSession(): AuthSession | null {
  const raw = localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthSession;
  } catch {
    return null;
  }
}

export function writeAuthSession(session: AuthSession | null): void {
  if (!session) {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    return;
  }
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
}

type RequestOptions = {
  query?: QueryParams;
  headers?: Record<string, string>;
  body?: unknown;
};

export async function apiRequest<T>(method: string, path: string, options?: RequestOptions): Promise<T> {
  const session = readAuthSession();
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...(options?.headers ?? {})
  };
  if (options?.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (session?.token) {
    headers.Authorization = `Bearer ${session.token}`;
  }
  if (session?.userId) {
    headers["X-User-Id"] = session.userId;
  }
  if (session?.login) {
    headers["X-User-Login"] = session.login;
  }

  const response = await fetch(buildUrl(path, options?.query), {
    method,
    headers,
    body: options?.body === undefined ? undefined : JSON.stringify(options.body)
  });

  if (!response.ok) {
    let payload: ApiErrorPayload = {};
    try {
      payload = (await response.json()) as ApiErrorPayload;
    } catch {
      // keep default payload
    }
    throw new ApiError(
      payload.error?.message ?? `Request failed with status ${response.status}`,
      response.status,
      payload.error?.code ?? "api_error",
      payload.correlationId,
      payload.error?.details
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

export const api = {
  get: <T>(path: string, query?: QueryParams) => apiRequest<T>("GET", path, { query }),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    apiRequest<T>("POST", path, { body, headers }),
  put: <T>(path: string, body?: unknown) => apiRequest<T>("PUT", path, { body }),
  delete: <T>(path: string) => apiRequest<T>("DELETE", path)
};
