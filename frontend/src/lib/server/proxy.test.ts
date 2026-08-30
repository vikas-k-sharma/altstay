import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { z } from 'zod';
import { proxy } from './proxy';
import { SESSION_COOKIE_NAME } from './session';

const RequestSchema = z.object({ name: z.string().min(1) });
const ResponseSchema = z.object({ id: z.string(), name: z.string() });

function makeRequest(body?: unknown, cookie?: string) {
  return new NextRequest('http://localhost:3000/api/console/widgets', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(cookie ? { Cookie: cookie } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

describe('proxy()', () => {
  const originalFetch = global.fetch;
  const originalBackendUrl = process.env.BACKEND_URL;

  beforeEach(() => {
    process.env.BACKEND_URL = 'http://localhost:8080';
  });

  afterEach(() => {
    global.fetch = originalFetch;
    if (originalBackendUrl !== undefined) {
      process.env.BACKEND_URL = originalBackendUrl;
    } else {
      delete process.env.BACKEND_URL;
    }
  });

  it('returns 400 without calling upstream when the request body fails validation', async () => {
    global.fetch = vi.fn();
    const handler = proxy({ method: 'POST', path: '/api/v1/widgets', requestSchema: RequestSchema });

    const response = await handler(makeRequest({ name: '' }));
    const data = await response.json();

    expect(response.status).toBe(400);
    expect(data.title).toBe('Validation Failure');
    expect(data.errors.name).toBeDefined();
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('relays the session cookie as JSESSIONID and validates the upstream response', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify({ id: 'w1', name: 'Widget' }), { status: 200 })
    );
    const handler = proxy({
      method: 'POST',
      path: '/api/v1/widgets',
      requestSchema: RequestSchema,
      responseSchema: ResponseSchema,
    });

    const response = await handler(makeRequest({ name: 'Widget' }, `${SESSION_COOKIE_NAME}=abc123`));
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data).toEqual({ id: 'w1', name: 'Widget' });

    const [url, init] = vi.mocked(global.fetch).mock.calls[0];
    expect(url).toBe('http://localhost:8080/api/v1/widgets');
    const headers = init?.headers as Headers;
    expect(headers.get('Cookie')).toBe('JSESSIONID=abc123');
  });

  it('passes a non-2xx upstream ProblemDetail through unchanged', async () => {
    const problem = { type: 'https://api.altstay.com/errors/not-found', title: 'Not Found', status: 404, detail: 'no such widget' };
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(problem), { status: 404 }));
    const handler = proxy({ method: 'GET', path: '/api/v1/widgets/missing' });

    const response = await handler(makeRequest());
    const data = await response.json();

    expect(response.status).toBe(404);
    expect(data).toEqual(problem);
  });

  it('clears the BFF session cookie on an upstream 401', async () => {
    const problem = { type: 'https://api.altstay.com/errors/unauthorized', title: 'Unauthorized', status: 401, detail: 'Invalid credentials' };
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(problem), { status: 401 }));
    const handler = proxy({ method: 'GET', path: '/api/v1/widgets' });

    const response = await handler(makeRequest(undefined, `${SESSION_COOKIE_NAME}=stale`));

    expect(response.status).toBe(401);
    const setCookie = response.cookies.get(SESSION_COOKIE_NAME);
    expect(setCookie?.value).toBe('');
  });

  it('returns 502 when the upstream response fails the response schema', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ oops: true }), { status: 200 }));
    const handler = proxy({ method: 'GET', path: '/api/v1/widgets', responseSchema: ResponseSchema });

    const response = await handler(makeRequest());
    const data = await response.json();

    expect(response.status).toBe(502);
    expect(data.title).toBe('Upstream Contract Mismatch');
  });

  it('returns a bare 204 when upstream returns 204', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 }));
    const handler = proxy({ method: 'DELETE', path: '/api/v1/widgets/w1' });

    const response = await handler(makeRequest());
    expect(response.status).toBe(204);
  });

  it('returns a bare success response when upstream succeeds with an empty body at any status', async () => {
    // Not every bodiless success is a 204 — e.g. the hybrid-mapping POST (§4.8) returns 201 with
    // nothing. NextResponse.json(undefined) throws, so this must not fall into the JSON branch.
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 201 }));
    const handler = proxy({ method: 'POST', path: '/api/v1/room-types/rt1/spaces/s1' });

    const response = await handler(makeRequest());
    expect(response.status).toBe(201);
    expect(await response.text()).toBe('');
  });

  it('derives the upstream path from the request when given a function', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ id: 'w1', name: 'Widget' }), { status: 200 }));
    const handler = proxy({
      method: 'GET',
      path: (req) => `/api/v1${req.nextUrl.pathname.replace('/api/console', '')}`,
    });

    await handler(makeRequest());

    const [url] = vi.mocked(global.fetch).mock.calls[0];
    expect(url).toBe('http://localhost:8080/api/v1/widgets');
  });
});
