import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { POST } from './route';
import spaceFixture from '@/lib/contracts/__fixtures__/space.json';

const validBody = {
  name: '305',
  floor: '3',
  isActive: true,
  units: [{ label: 'Bed 1', unitKind: 'SINGLE', isActive: true }],
};

function postRequest(slug: string, body: unknown) {
  return new NextRequest(`http://localhost:3000/api/console/properties/${slug}/spaces`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('POST /api/console/properties/[slug]/spaces', () => {
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

  it('returns 400 without calling upstream on an unrecognised unitKind', async () => {
    global.fetch = vi.fn();
    const response = await POST(
      postRequest('driftwood-goa', { ...validBody, units: [{ label: 'Bed 1', unitKind: 'TRIPLE', isActive: true }] })
    );
    expect(response.status).toBe(400);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('derives the slug from the URL and creates a space', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(spaceFixture), { status: 201 }));

    const response = await POST(postRequest('driftwood-goa', validBody));

    expect(response.status).toBe(201);
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe(
      'http://localhost:8080/api/v1/properties/driftwood-goa/spaces'
    );
  });
});
