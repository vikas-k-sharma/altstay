import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { POST } from './route';
import roomTypeFixture from '@/lib/contracts/__fixtures__/room-type.json';

const validBody = {
  code: 'MIXED-6',
  name: '6-bed mixed dorm',
  saleMode: 'PER_UNIT',
  kind: 'DORM',
  maxOccupancy: 6,
  baseRateMinor: 65000,
  description: null,
  isActive: true,
};

function postRequest(slug: string, body: unknown) {
  return new NextRequest(`http://localhost:3000/api/console/properties/${slug}/room-types`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('POST /api/console/properties/[slug]/room-types', () => {
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

  it('returns 400 without calling upstream on an unrecognised kind', async () => {
    global.fetch = vi.fn();
    const response = await POST(postRequest('driftwood-goa', { ...validBody, kind: 'SUITE' }));
    expect(response.status).toBe(400);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('derives the slug from the URL and creates a room type', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(roomTypeFixture), { status: 201 }));

    const response = await POST(postRequest('driftwood-goa', validBody));
    const data = await response.json();

    expect(response.status).toBe(201);
    expect(data.code).toBe('MIXED-6');
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe(
      'http://localhost:8080/api/v1/properties/driftwood-goa/room-types'
    );
  });
});
