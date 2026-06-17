import { HttpResponse, http } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '../test/server';
import { RequestError, request } from './client';
import {
  deleteCollection,
  getCollections,
  login,
  me,
  register,
} from './endpoints';
import { getToken, registerUnauthorizedHandler, setToken } from './tokenStore';

// All requests go to the /api-prefixed, same-origin path. The wildcard prefix
// keeps the matcher independent of the jsdom origin.
const API = '*/api';

afterEach(() => {
  setToken(null);
  registerUnauthorizedHandler(null);
});

describe('request — auth header', () => {
  it('attaches Authorization: Bearer <token> when a token is set', async () => {
    setToken('jwt-123');
    let seen: string | null = 'MISSING';
    server.use(
      http.get(`${API}/collections`, ({ request }) => {
        seen = request.headers.get('authorization');
        return HttpResponse.json([]);
      }),
    );

    await getCollections();

    expect(seen).toBe('Bearer jwt-123');
  });

  it('omits Authorization when no token is set', async () => {
    let seen: string | null = 'MISSING';
    server.use(
      http.get(`${API}/collections`, ({ request }) => {
        seen = request.headers.get('authorization');
        return HttpResponse.json([]);
      }),
    );

    await getCollections();

    expect(seen).toBeNull();
  });
});

describe('request — path prefixing', () => {
  it('calls the /api-prefixed path for an endpoint', async () => {
    let hit = false;
    server.use(
      http.post(`${API}/auth/login`, async () => {
        hit = true;
        return HttpResponse.json({ token: 't', user: { id: 1, username: 'a' } });
      }),
    );

    const res = await login({ username: 'a', password: 'b' });

    expect(hit).toBe(true);
    expect(res.token).toBe('t');
  });
});

describe('request — success body parsing', () => {
  it('parses and returns a JSON body for 200', async () => {
    server.use(
      http.get(`${API}/collections`, () =>
        HttpResponse.json([{ id: 1, name: 'C', ownerId: 1, createdAt: 'now' }]),
      ),
    );

    const collections = await getCollections();

    expect(collections).toEqual([{ id: 1, name: 'C', ownerId: 1, createdAt: 'now' }]);
  });

  it('resolves 204 No Content to undefined without parsing JSON', async () => {
    server.use(
      http.delete(`${API}/collections/5`, () => new HttpResponse(null, { status: 204 })),
    );

    const result = await deleteCollection(5);

    expect(result).toBeUndefined();
  });

  it('handles an empty 200 body safely (resolves to null)', async () => {
    server.use(http.get(`${API}/collections`, () => new HttpResponse('', { status: 200 })));

    const result = await getCollections();

    expect(result).toBeNull();
  });

  it('handles a non-JSON 200 body safely (resolves to null)', async () => {
    server.use(
      http.get(
        `${API}/collections`,
        () => new HttpResponse('not json', { status: 200 }),
      ),
    );

    const result = await getCollections();

    expect(result).toBeNull();
  });
});

describe('request — error handling', () => {
  it('throws a typed RequestError carrying status and parsed body', async () => {
    server.use(
      http.post(`${API}/auth/login`, () =>
        HttpResponse.json(
          { error: 'Invalid credentials', fields: { username: 'required' } },
          { status: 400 },
        ),
      ),
    );

    const err = await login({ username: 'a', password: 'b' }).catch((e) => e);

    expect(err).toBeInstanceOf(RequestError);
    expect((err as RequestError).status).toBe(400);
    expect((err as RequestError).body).toEqual({
      error: 'Invalid credentials',
      fields: { username: 'required' },
    });
    expect((err as RequestError).message).toBe('Invalid credentials');
  });

  it('falls back to a status message when the error body is absent', async () => {
    server.use(
      http.get(`${API}/collections`, () => new HttpResponse(null, { status: 500 })),
    );

    const err = await getCollections().catch((e) => e);

    expect(err).toBeInstanceOf(RequestError);
    expect((err as RequestError).status).toBe(500);
    expect((err as RequestError).body).toBeNull();
    expect((err as RequestError).message).toBe('Request failed with status 500');
  });
});

describe('request — 401 handling', () => {
  it('invokes the unauthorized handler once, then throws (no loop)', async () => {
    const onUnauthorized = vi.fn();
    registerUnauthorizedHandler(onUnauthorized);

    let calls = 0;
    server.use(
      http.get(`${API}/collections`, () => {
        calls += 1;
        return HttpResponse.json({ error: 'Unauthorized' }, { status: 401 });
      }),
    );

    const err = await getCollections().catch((e) => e);

    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(calls).toBe(1); // single fetch — does not retry/loop
    expect(err).toBeInstanceOf(RequestError);
    expect((err as RequestError).status).toBe(401);
  });

  it('does not invoke the unauthorized handler for non-401 errors', async () => {
    const onUnauthorized = vi.fn();
    registerUnauthorizedHandler(onUnauthorized);
    server.use(
      http.get(`${API}/collections`, () =>
        HttpResponse.json({ error: 'Bad' }, { status: 400 }),
      ),
    );

    await getCollections().catch(() => undefined);

    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('still invokes the handler for a 401 on a normal endpoint (me)', async () => {
    const onUnauthorized = vi.fn();
    registerUnauthorizedHandler(onUnauthorized);
    server.use(
      http.get(`${API}/auth/me`, () =>
        HttpResponse.json({ error: 'Unauthorized' }, { status: 401 }),
      ),
    );

    const err = await me().catch((e) => e);

    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(err).toBeInstanceOf(RequestError);
    expect((err as RequestError).status).toBe(401);
  });

  it('does NOT invoke the handler for a 401 from login (skipAuthRedirect)', async () => {
    const onUnauthorized = vi.fn();
    registerUnauthorizedHandler(onUnauthorized);
    server.use(
      http.post(`${API}/auth/login`, () =>
        HttpResponse.json({ error: 'Invalid credentials' }, { status: 401 }),
      ),
    );

    const err = await login({ username: 'a', password: 'b' }).catch((e) => e);

    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(err).toBeInstanceOf(RequestError);
    expect((err as RequestError).status).toBe(401);
    expect((err as RequestError).message).toBe('Invalid credentials');
  });

  it('does NOT invoke the handler for a 401 from register (skipAuthRedirect)', async () => {
    const onUnauthorized = vi.fn();
    registerUnauthorizedHandler(onUnauthorized);
    server.use(
      http.post(`${API}/auth/register`, () =>
        HttpResponse.json({ error: 'Invalid credentials' }, { status: 401 }),
      ),
    );

    const err = await register({ username: 'a', password: 'b' }).catch((e) => e);

    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(err).toBeInstanceOf(RequestError);
    expect((err as RequestError).status).toBe(401);
  });
});

describe('request — content type', () => {
  it('sends Content-Type: application/json and keeps the token in memory only', async () => {
    setToken('jwt-xyz');
    let contentType: string | null = null;
    server.use(
      http.post(`${API}/collections`, ({ request }) => {
        contentType = request.headers.get('content-type');
        return HttpResponse.json({ id: 1 });
      }),
    );

    await request('/collections', { method: 'POST', body: JSON.stringify({ name: 'x' }) });

    expect(contentType).toContain('application/json');
    // Sanity: the token is read from the in-memory store, not from storage.
    expect(getToken()).toBe('jwt-xyz');
  });
});
