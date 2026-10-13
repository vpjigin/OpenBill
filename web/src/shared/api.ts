// Tiny fetch wrapper. Each app keeps its own token in its own origin's localStorage
// (admin on :9090, billing on :8080), so the two sign-ins never collide.

export type Role = "ADMIN" | "MANAGER" | "CASHIER";
export type Channel = "ADMIN" | "LOCAL" | "LAN" | "PUBLIC";

export interface CurrentUser {
  userId: string;
  tokenId: string;
  name: string;
  username: string;
  role: Role;
  channel: Channel;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

const TOKEN_KEY = "billing.token";

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage unavailable: session lasts until reload */
  }
}

type Listener = () => void;
const unauthorizedListeners = new Set<Listener>();
export function onUnauthorized(fn: Listener) {
  unauthorizedListeners.add(fn);
  return () => unauthorizedListeners.delete(fn);
}

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = readToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  let body = init.body;
  if (init.json !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(init.json);
  }
  const res = await fetch(path, { ...init, headers, body });
  if (res.status === 401 && token) {
    setToken(null);
    unauthorizedListeners.forEach((fn) => fn());
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const data = await res.json();
      if (data?.error) message = data.error;
    } catch {
      /* not JSON */
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204 || res.headers.get("content-length") === "0") return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function login(username: string, password: string): Promise<CurrentUser> {
  const r = await api<{ token: string; user: CurrentUser }>("/api/auth/login", {
    method: "POST",
    json: { username, password },
  });
  setToken(r.token);
  return r.user;
}

export async function me(): Promise<CurrentUser | null> {
  if (!readToken()) return null;
  try {
    return await api<CurrentUser>("/api/auth/me");
  } catch {
    return null;
  }
}

export async function logout() {
  try {
    await api("/api/auth/logout", { method: "POST" });
  } finally {
    setToken(null);
  }
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await api("/api/auth/change-password", {
    method: "POST",
    json: { currentPassword, newPassword },
  });
}
