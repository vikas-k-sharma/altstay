import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { POST } from './route';
import authUserFixture from '@/lib/contracts/__fixtures__/auth-user.json';
import problemUnauthorized from '@/lib/contracts/__fixtures__/problem-unauthorized.json';

function loginRequest(body: unknown) {
  return new NextRequest('http://localhost:3000/api/console/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('POST /api/console/login', () => {
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
    vi.unstubAllEnvs();
  });

  it('returns 400 without calling upstream on a validation failure', async () => {
    global.fetch = vi.fn();

    const response = await POST(loginRequest({ tenantSlug: '', email: '', password: '' }));
    const data = await response.json();

    expect(response.status).toBe(400);
    expect(data.title).toBe('Validation Failure');
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('passes through the backend refusal unchanged, with no cookie set', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify(problemUnauthorized), { status: 401 })
    );

    const response = await POST(
      loginRequest({ tenantSlug: 'driftwood', email: 'owner@driftwood.example', password: 'wrong' })
    );
    const data = await response.json();

    expect(response.status).toBe(401);
    expect(data.detail).toBe('Invalid credentials');
    expect(response.headers.get('set-cookie')).toBeNull();
  });

  it('relays the upstream JSESSIONID under altstay_session with the required attributes', async () => {
    vi.stubEnv('NODE_ENV', 'production');
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify(authUserFixture), {
        status: 200,
        headers: { 'set-cookie': 'JSESSIONID=upstream-session-value; Path=/; HttpOnly; SameSite=Strict' },
      })
    );

    const response = await POST(
      loginRequest({ tenantSlug: 'driftwood', email: 'owner@driftwood.example', password: 'hunter2' })
    );
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data.tenantSlug).toBe('driftwood');

    const setCookie = response.headers.get('set-cookie') ?? '';
    expect(setCookie).toContain('altstay_session=upstream-session-value');
    expect(setCookie).toMatch(/HttpOnly/i);
    expect(setCookie).toMatch(/SameSite=strict/i);
    expect(setCookie).toContain('Path=/');
    expect(setCookie).toMatch(/Secure/i);
    expect(setCookie).not.toMatch(/Max-Age/i);
  });

  it('does not mark the cookie Secure outside production', async () => {
    vi.stubEnv('NODE_ENV', 'development');
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify(authUserFixture), {
        status: 200,
        headers: { 'set-cookie': 'JSESSIONID=dev-session-value; Path=/; HttpOnly; SameSite=Strict' },
      })
    );

    const response = await POST(
      loginRequest({ tenantSlug: 'driftwood', email: 'owner@driftwood.example', password: 'hunter2' })
    );

    const setCookie = response.headers.get('set-cookie') ?? '';
    expect(setCookie).not.toMatch(/Secure/i);
  });

  it('returns 502 when the upstream body does not carry a valid AuthUserResponse', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify({ oops: true }), {
        status: 200,
        headers: { 'set-cookie': 'JSESSIONID=abc; Path=/' },
      })
    );

    const response = await POST(
      loginRequest({ tenantSlug: 'driftwood', email: 'owner@driftwood.example', password: 'hunter2' })
    );
    const data = await response.json();

    expect(response.status).toBe(502);
    expect(data.title).toBe('Upstream Contract Mismatch');
  });

  it('returns 502 when the upstream response carries no Set-Cookie at all', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify(authUserFixture), { status: 200 })
    );

    const response = await POST(
      loginRequest({ tenantSlug: 'driftwood', email: 'owner@driftwood.example', password: 'hunter2' })
    );

    expect(response.status).toBe(502);
  });
});
