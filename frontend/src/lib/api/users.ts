import type { AuthUser } from "@/types/auth";
import { getAuthenticatedClient } from "@/lib/api/authenticated-client";

export type UpdateUserInput = {
  firstName?: string;
  lastName?: string;
  role?: AuthUser["role"];
  enabled?: boolean;
};

export async function listUsers(): Promise<AuthUser[]> {
  const client = getAuthenticatedClient();
  return client.get<AuthUser[]>("/api/v1/users");
}

export async function updateUser(userId: string, input: UpdateUserInput): Promise<AuthUser> {
  const client = getAuthenticatedClient();
  return client.patch<AuthUser>(`/api/v1/users/${userId}`, input);
}
