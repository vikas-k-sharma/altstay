import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { POST } from './route';
import quoteFixture from '@/lib/contracts/__fixtures__/quote.json';

function quoteRequest(body: unknown) {
  return new NextRequest('http://localhost:3000/api/console/quote', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

const validBody = {
  propertyId: '7ed13bba-74e2-4608-83c6-bedb10b9e5bd',
  roomTypeId: 'd812b0fb-cd5d-4ed7-a4d2-0a12caf6b118',
  checkIn: '2026-08-30',
  checkOut: '2026-09-02',
  unitCount: 1,
};

describe('POST /api/console/quote', () => {
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

  it('returns 400 without calling upstream on an invalid unitCount', async () => {
    global.fetch = vi.fn();
    const response = await POST(quoteRequest({ ...validBody, unitCount: 0 }));
    expect(response.status).toBe(400);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('relays a valid quote request and validates the response', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(quoteFixture), { status: 200 }));

    const response = await POST(quoteRequest(validBody));
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data.totalMinor).toBe(218400);
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe('http://localhost:8080/api/v1/bookings/quote');
  });
});
