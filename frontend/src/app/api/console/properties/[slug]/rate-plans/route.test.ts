import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { POST } from './route';
import ratePlanFixture from '@/lib/contracts/__fixtures__/rate-plan.json';

const validBody = { roomTypeId: ratePlanFixture.roomTypeId, code: 'STANDARD', name: 'Standard rate', isDefault: true };

function postRequest(slug: string, body: unknown) {
  return new NextRequest(`http://localhost:3000/api/console/properties/${slug}/rate-plans`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('POST /api/console/properties/[slug]/rate-plans', () => {
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

  it('returns 400 without calling upstream when code is blank', async () => {
    global.fetch = vi.fn();
    const response = await POST(postRequest('driftwood-goa', { ...validBody, code: '' }));
    expect(response.status).toBe(400);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('derives the slug from the URL and creates the rate plan', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(ratePlanFixture), { status: 201 }));

    const response = await POST(postRequest('driftwood-goa', validBody));

    expect(response.status).toBe(201);
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe(
      'http://localhost:8080/api/v1/properties/driftwood-goa/rate-plans'
    );
  });

  it('passes through a 409 when a second default plan collides with the DB constraint', async () => {
    const problem = {
      type: 'https://api.altstay.com/errors/booking-conflict',
      title: 'Conflict',
      status: 409,
      detail: 'That change conflicts with something already recorded.',
    };
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(problem), { status: 409 }));

    const response = await POST(postRequest('driftwood-goa', validBody));
    expect(response.status).toBe(409);
  });
});
