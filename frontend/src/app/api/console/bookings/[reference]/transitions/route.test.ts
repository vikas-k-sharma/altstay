import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { POST } from './route';
import bookingFixture from '@/lib/contracts/__fixtures__/booking.json';
import { SESSION_COOKIE_NAME } from '@/lib/server/session';

function transitionRequest(reference: string, body: unknown, cookie?: string) {
  return new NextRequest(`http://localhost:3000/api/console/bookings/${reference}/transitions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(cookie ? { Cookie: cookie } : {}),
    },
    body: JSON.stringify(body),
  });
}

describe('POST /api/console/bookings/[reference]/transitions', () => {
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

  it('derives the reference from the URL and relays the session cookie', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify(bookingFixture), { status: 200 })
    );

    const response = await POST(
      transitionRequest('ALT7F3K9Q', { to: 'CHECKED_IN', reason: null }, `${SESSION_COOKIE_NAME}=abc123`)
    );

    expect(response.status).toBe(200);
    const [url, init] = vi.mocked(global.fetch).mock.calls[0];
    expect(url).toBe('http://localhost:8080/api/v1/bookings/ALT7F3K9Q/transitions');
    const headers = init?.headers as Headers;
    expect(headers.get('Cookie')).toBe('JSESSIONID=abc123');
  });

  it('passes through a 409 invalid-booking-transition unchanged', async () => {
    const problem = {
      type: 'https://api.altstay.com/errors/invalid-booking-transition',
      title: 'Invalid Booking Transition',
      status: 409,
      detail: 'Cannot transition from CHECKED_OUT to CHECKED_IN',
    };
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(problem), { status: 409 }));

    const response = await POST(transitionRequest('ALT7F3K9Q', { to: 'CHECKED_IN', reason: null }));
    const data = await response.json();

    expect(response.status).toBe(409);
    expect(data.type).toBe(problem.type);
  });

  it('returns 400 without calling upstream on an illegal target status', async () => {
    global.fetch = vi.fn();

    const response = await POST(transitionRequest('ALT7F3K9Q', { to: 'DELETED', reason: null }));

    expect(response.status).toBe(400);
    expect(global.fetch).not.toHaveBeenCalled();
  });
});
