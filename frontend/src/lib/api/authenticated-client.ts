import { createApiClient } from "@/lib/api/client";
import { getAccessToken } from "@/lib/auth/session";

let client = createApiClient({
  getAccessToken,
  credentials: "include",
});

export function getAuthenticatedClient() {
  return client;
}

export function resetAuthenticatedClient() {
  client = createApiClient({
    getAccessToken,
    credentials: "include",
  });
}
