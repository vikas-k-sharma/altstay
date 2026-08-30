import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { PUT } from './route';
import roomTypeFixture from '@/lib/contracts/__fixtures__/room-type.json';

const validBody = {
  name: '6-bed mixed dorm',
  saleMode: 'PER_UNIT',
  kind: 'DORM',
  maxOccupancy: 6,
  baseRateMinor: 65000,
  description: null,
  isActive: true,
};

function putRequest(slug: string, id: string, body: unknown) {
  return new NextRequest(`http://localhost:3000/api/console/properties/${slug}/room-types/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('PUT /api/console/properties/[slug]/room-types/[id]', () => {
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

  it('derives both the slug and the room type id from the URL', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(roomTypeFixture), { status: 200 }));

    const response = await PUT(putRequest('driftwood-goa', roomTypeFixture.id, validBody));

    expect(response.status).toBe(200);
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe(
      `http://localhost:8080/api/v1/properties/driftwood-goa/room-types/${roomTypeFixture.id}`
    );
  });
});
