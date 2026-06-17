import type { ApiError } from './types';
import { getToken, notifyUnauthorized } from './tokenStore';

// All frontend calls are namespaced under /api; the Vite dev proxy strips the
// prefix and forwards to the backend root (see vite.config.ts).
const API_PREFIX = '/api';

const HTTP_UNAUTHORIZED = 401;
const HTTP_NO_CONTENT = 204;

// A typed error thrown for any non-2xx response. Carries the HTTP status and the
// parsed ApiError body (when present) so callers can surface a readable message.
export class RequestError extends Error {
  readonly status: number;
  readonly body: ApiError | null;

  constructor(status: number, body: ApiError | null) {
    super(body?.error ?? `Request failed with status ${status}`);
    this.name = 'RequestError';
    this.status = status;
    this.body = body;
  }
}

async function parseJsonSafely(res: Response): Promise<unknown> {
  // 204 and empty bodies must not be parsed as JSON.
  if (res.status === HTTP_NO_CONTENT) return null;
  const text = await res.text();
  if (text.length === 0) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

// Per-request options. `skipAuthRedirect` opts a call out of the global 401
// handler — used by the login/register flow, where a 401 means "invalid
// credentials" (surface it in the form) rather than "session expired" (logout).
export interface RequestConfig {
  skipAuthRedirect?: boolean;
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
  config: RequestConfig = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Content-Type', 'application/json');

  const token = getToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const res = await fetch(`${API_PREFIX}${path}`, { ...options, headers });

  if (!res.ok) {
    const body = (await parseJsonSafely(res)) as ApiError | null;
    // Centralized 401 handling: clear auth state + redirect before throwing.
    // Auth calls opt out so a failed login surfaces in the form instead.
    if (res.status === HTTP_UNAUTHORIZED && !config.skipAuthRedirect) {
      notifyUnauthorized();
    }
    throw new RequestError(res.status, body);
  }

  if (res.status === HTTP_NO_CONTENT) {
    return undefined as T;
  }

  return (await parseJsonSafely(res)) as T;
}
