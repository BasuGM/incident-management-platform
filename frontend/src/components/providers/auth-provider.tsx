"use client";

import {
  fetchCurrentUser,
  loginUser,
  logoutUser,
  refreshSession,
  registerUser,
  type LoginInput,
  type RegisterInput,
} from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";
import { clearAccessToken, getAccessToken, setAccessToken } from "@/lib/auth/session";
import type { AuthUser } from "@/types/auth";
import { useQueryClient } from "@tanstack/react-query";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

type AuthContextValue = {
  user: AuthUser | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (input: LoginInput) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

function applyAuthResponse(response: Awaited<ReturnType<typeof loginUser>>) {
  setAccessToken(response.accessToken, response.expiresIn);
  return response.user;
}

async function invalidateTenantQueries(queryClient: ReturnType<typeof useQueryClient>) {
  await queryClient.invalidateQueries({ queryKey: ["organizations"] });
  await queryClient.invalidateQueries({ queryKey: ["organization"] });
  await queryClient.invalidateQueries({ queryKey: ["incidents"] });
  await queryClient.invalidateQueries({ queryKey: ["incident"] });
  await queryClient.invalidateQueries({ queryKey: ["services"] });
  await queryClient.invalidateQueries({ queryKey: ["service"] });
  await queryClient.invalidateQueries({ queryKey: ["teams"] });
  await queryClient.invalidateQueries({ queryKey: ["team"] });
  await queryClient.invalidateQueries({ queryKey: ["organization-members"] });
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      try {
        if (getAccessToken()) {
          const currentUser = await fetchCurrentUser();
          if (active) {
            setUser(currentUser);
          }
          return;
        }
        const refreshed = await refreshSession();
        if (active) {
          setUser(applyAuthResponse(refreshed));
        }
      } catch {
        clearAccessToken();
        if (active) {
          setUser(null);
        }
      } finally {
        if (active) {
          setIsLoading(false);
        }
      }
    }

    void bootstrap();
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (input: LoginInput) => {
    const response = await loginUser(input);
    setUser(applyAuthResponse(response));
    await invalidateTenantQueries(queryClient);
  }, [queryClient]);

  const register = useCallback(async (input: RegisterInput) => {
    const response = await registerUser(input);
    setUser(applyAuthResponse(response));
    await invalidateTenantQueries(queryClient);
  }, [queryClient]);

  const logout = useCallback(async () => {
    try {
      await logoutUser();
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) {
        throw error;
      }
    } finally {
      clearAccessToken();
      setUser(null);
      await invalidateTenantQueries(queryClient);
    }
  }, [queryClient]);

  const refresh = useCallback(async () => {
    const response = await refreshSession();
    setUser(applyAuthResponse(response));
  }, []);

  const value = useMemo(
    () => ({
      user,
      isLoading,
      isAuthenticated: user !== null,
      login,
      register,
      logout,
      refresh,
    }),
    [user, isLoading, login, register, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
