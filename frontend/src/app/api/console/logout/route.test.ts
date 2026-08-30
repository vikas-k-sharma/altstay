import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { POST } from './route';
import { SESSION_COOKIE_NAME } from '@/lib/server/session';

function logoutRequest(cookie?: string) {
  return new NextRequest('http://localhost:3000/api/console/logout', {
    method: 'POST',
    headers: cookie ? { Cookie: cookie } : undefined,
  });
}

describe('POST /api/console/logout', () => {
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

  it('calls Spring logout with the session cookie, then clears the BFF cookie', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 }));

    const response = await POST(logoutRequest(`${SESSION_COOKIE_NAME}=abc123`));

    expect(global.fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/auth/logout',
      expect.objectContaining({ method: 'POST' })
    );
    const [, init] = vi.mocked(global.fetch).mock.calls[0];
    const headers = init?.headers as Headers;
    expect(headers.get('Cookie')).toBe('JSESSIONID=abc123');

    expect(response.status).toBe(204);
    const setCookie = response.headers.get('set-cookie') ?? '';
    expect(setCookie).toContain(`${SESSION_COOKIE_NAME}=`);
    expect(setCookie).toMatch(/Expires=Thu, 01 Jan 1970/i);
  });

  it('does not call Spring when there is no session cookie to relay, and still clears the BFF cookie', async () => {
    global.fetch = vi.fn();

    const response = await POST(logoutRequest());

    expect(global.fetch).not.toHaveBeenCalled();
    expect(response.status).toBe(204);
    expect(response.headers.get('set-cookie') ?? '').toContain(`${SESSION_COOKIE_NAME}=`);
  });
});
