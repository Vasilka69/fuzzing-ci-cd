import { api } from "@/api/client";
import type { AuthLoginRequest, AuthLoginResponse, AuthMeResponse } from "@/shared/types/auth";

export const authApi = {
  login: (payload: AuthLoginRequest) => api.post<AuthLoginResponse>("/auth/login", payload),
  me: () => api.get<AuthMeResponse>("/auth/me")
};
