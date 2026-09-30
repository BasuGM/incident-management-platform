import type { AuthResponse, AuthUser } from "@/types/auth";
import { getAuthenticatedClient } from "@/lib/api/authenticated-client";

export type RegisterInput = {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
};

export type LoginInput = {
  email: string;
  password: string;
};

export async function registerUser(input: RegisterInput): Promise<AuthResponse> {
  const client = getAuthenticatedClient();
  return client.post<AuthResponse>("/api/v1/auth/register", input);
}

export async function loginUser(input: LoginInput): Promise<AuthResponse> {
  const client = getAuthenticatedClient();
  return client.post<AuthResponse>("/api/v1/auth/login", input);
}

export async function refreshSession(): Promise<AuthResponse> {
  const client = getAuthenticatedClient();
  return client.post<AuthResponse>("/api/v1/auth/refresh", {});
}

export async function logoutUser(): Promise<void> {
  const client = getAuthenticatedClient();
  await client.post<void>("/api/v1/auth/logout", {});
}

export async function fetchCurrentUser(): Promise<AuthUser> {
  const client = getAuthenticatedClient();
  return client.get<AuthUser>("/api/v1/users/me");
}
