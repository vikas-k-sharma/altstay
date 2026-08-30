import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { POST } from './route';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';
import { SESSION_COOKIE_NAME, PROPERTY_COOKIE_NAME } from '@/lib/server/session';

function switchRequest(body: unknown, cookie?: string) {
  return new NextRequest('http://localhost:3000/api/console/property', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(cookie ? { Cookie: cookie } : {}),
    },
    body: JSON.stringify(body),
  });
}

describe('POST /api/console/property', () => {
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

  it('returns 401 with no session cookie, without calling upstream', async () => {
    global.fetch = vi.fn();

    const response = await POST(switchRequest({ slug: 'driftwood-goa' }));

    expect(response.status).toBe(401);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('sets the property cookie when the slug belongs to the tenant', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify([propertyFixture]), { status: 200 })
    );

    const response = await POST(
      switchRequest({ slug: 'driftwood-goa' }, `${SESSION_COOKIE_NAME}=abc123`)
    );

    expect(response.status).toBe(204);
    const setCookie = response.headers.get('set-cookie') ?? '';
    expect(setCookie).toContain(`${PROPERTY_COOKIE_NAME}=driftwood-goa`);
  });

  it('refuses a slug absent from the tenant\'s own property list', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify([propertyFixture]), { status: 200 })
    );

    const response = await POST(
      switchRequest({ slug: 'someone-elses-hostel' }, `${SESSION_COOKIE_NAME}=abc123`)
    );

    expect(response.status).toBe(404);
    expect(response.headers.get('set-cookie')).toBeNull();
  });
});
