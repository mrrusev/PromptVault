// Module-level token holder. The fetch client is plain TS (no hooks), so it reads
// the bearer token from here rather than from React state.
//
// IMPORTANT: the token lives in memory only. It is never written to localStorage,
// sessionStorage, or cookies. A full page refresh or a new tab loses it by design.

let token: string | null = null;
let onUnauthorized: (() => void) | null = null;

export function getToken(): string | null {
  return token;
}

export function setToken(next: string | null): void {
  token = next;
}

// AuthProvider registers a handler on mount so the client can react to a 401
// (clear state + redirect to /login) without importing React or router code.
export function registerUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

export function notifyUnauthorized(): void {
  onUnauthorized?.();
}
