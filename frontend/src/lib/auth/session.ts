type SessionListener = () => void;

const ACCESS_TOKEN_KEY = "imp.accessToken";
const ACCESS_TOKEN_EXPIRES_AT_KEY = "imp.accessTokenExpiresAt";

let accessToken: string | null = null;
let accessTokenExpiresAt: number | null = null;
let hydratedFromStorage = false;
const listeners = new Set<SessionListener>();

function canUseSessionStorage(): boolean {
  return typeof window !== "undefined" && typeof window.sessionStorage !== "undefined";
}

function hydrateFromSessionStorage() {
  if (hydratedFromStorage || !canUseSessionStorage()) {
    return;
  }
  hydratedFromStorage = true;

  const storedToken = window.sessionStorage.getItem(ACCESS_TOKEN_KEY);
  const storedExpiresAt = window.sessionStorage.getItem(ACCESS_TOKEN_EXPIRES_AT_KEY);
  if (!storedToken || !storedExpiresAt) {
    return;
  }

  const expiresAt = Number.parseInt(storedExpiresAt, 10);
  if (!Number.isFinite(expiresAt) || Date.now() >= expiresAt) {
    window.sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    window.sessionStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_KEY);
    return;
  }

  accessToken = storedToken;
  accessTokenExpiresAt = expiresAt;
}

function persistToSessionStorage() {
  if (!canUseSessionStorage()) {
    return;
  }
  if (!accessToken || !accessTokenExpiresAt) {
    window.sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    window.sessionStorage.removeItem(ACCESS_TOKEN_EXPIRES_AT_KEY);
    return;
  }
  window.sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  window.sessionStorage.setItem(ACCESS_TOKEN_EXPIRES_AT_KEY, String(accessTokenExpiresAt));
}

export function getAccessToken(): string | null {
  hydrateFromSessionStorage();
  if (!accessToken) {
    return null;
  }
  if (accessTokenExpiresAt && Date.now() >= accessTokenExpiresAt) {
    clearAccessToken();
    return null;
  }
  return accessToken;
}

export function setAccessToken(token: string, expiresInSeconds: number) {
  accessToken = token;
  accessTokenExpiresAt = Date.now() + expiresInSeconds * 1000 - 5_000;
  persistToSessionStorage();
  notify();
}

export function clearAccessToken() {
  accessToken = null;
  accessTokenExpiresAt = null;
  persistToSessionStorage();
  notify();
}

export function subscribeSession(listener: SessionListener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function notify() {
  listeners.forEach((listener) => listener());
}
