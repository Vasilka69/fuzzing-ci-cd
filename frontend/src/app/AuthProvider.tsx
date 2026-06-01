import { authApi } from "@/api/authApi";
import { resolveCapabilities, type Capability } from "@/app/capabilities";
import { readAuthSession, writeAuthSession } from "@/api/client";
import type { AuthSession } from "@/shared/types/auth";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createContext, useContext, useMemo, useState, type PropsWithChildren } from "react";

type AuthContextValue = {
  session: AuthSession | null;
  roles: string[];
  isAuthenticated: boolean;
  meLogin: string | null;
  hasCapability: (capability: Capability) => boolean;
  login: (credentials: { login: string; password: string }) => Promise<void>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<AuthSession | null>(() => readAuthSession());
  const queryClient = useQueryClient();

  const meQuery = useQuery({
    queryKey: ["auth", "me", session?.userId],
    queryFn: authApi.me,
    enabled: Boolean(session?.userId)
  });

  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: (response) => {
      const newSession: AuthSession = {
        userId: response.userId,
        login: response.login,
        token: response.token
      };
      writeAuthSession(newSession);
      setSession(newSession);
      queryClient.invalidateQueries({ queryKey: ["auth", "me"] });
    }
  });

  const roles = meQuery.data?.roles ?? [];
  const capabilities = resolveCapabilities(roles);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      roles,
      isAuthenticated: Boolean(session?.userId),
      meLogin: meQuery.data?.login ?? session?.login ?? null,
      hasCapability: (capability: Capability) => capabilities.has(capability),
      login: async (credentials) => {
        await loginMutation.mutateAsync(credentials);
      },
      logout: () => {
        writeAuthSession(null);
        setSession(null);
        queryClient.clear();
      }
    }),
    [session, roles, meQuery.data?.login, capabilities, loginMutation, queryClient]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
