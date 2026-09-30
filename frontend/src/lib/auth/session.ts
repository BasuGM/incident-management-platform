type SessionListener = () => void;

let accessToken: string | null = null;
let accessTokenExpiresAt: number | null = null;
const listeners = new Set<SessionListener>();

export function getAccessToken(): string | null {
  if (!accessToken) {
    return null;
  }
  if (accessTokenExpiresAt && Date.now() >= accessTokenExpiresAt) {
    return null;
  }
  return accessToken;
}

export function setAccessToken(token: string, expiresInSeconds: number) {
  accessToken = token;
  accessTokenExpiresAt = Date.now() + expiresInSeconds * 1000 - 5_000;
  notify();
}

export function clearAccessToken() {
  accessToken = null;
  accessTokenExpiresAt = null;
  notify();
}

export function subscribeSession(listener: SessionListener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function notify() {
  listeners.forEach((listener) => listener());
}
