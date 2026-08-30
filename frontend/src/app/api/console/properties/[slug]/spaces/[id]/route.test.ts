import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { PUT } from './route';
import spaceFixture from '@/lib/contracts/__fixtures__/space.json';

const validBody = {
  name: '101',
  floor: '1',
  isActive: true,
  units: [{ label: 'Bed 1', unitKind: 'BUNK_TOP', isActive: true }],
};

function putRequest(slug: string, id: string, body: unknown) {
  return new NextRequest(`http://localhost:3000/api/console/properties/${slug}/spaces/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('PUT /api/console/properties/[slug]/spaces/[id]', () => {
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

  it('derives both the slug and the space id from the URL', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(spaceFixture), { status: 200 }));

    const response = await PUT(putRequest('driftwood-goa', spaceFixture.id, validBody));

    expect(response.status).toBe(200);
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe(
      `http://localhost:8080/api/v1/properties/driftwood-goa/spaces/${spaceFixture.id}`
    );
  });
});
