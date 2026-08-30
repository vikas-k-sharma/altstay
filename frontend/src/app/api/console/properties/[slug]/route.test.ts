import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { PUT } from './route';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';

const validBody = {
  name: 'Driftwood Beach Hostel',
  legalName: null,
  description: null,
  status: 'ACTIVE',
  timezone: 'Asia/Kolkata',
  currencyCode: 'INR',
  countryCode: 'IN',
  addressLine1: null,
  addressLine2: null,
  city: null,
  stateRegion: null,
  postalCode: null,
  contactEmail: null,
  contactPhone: null,
  checkInTime: '14:00:00',
  checkOutTime: '11:00:00',
  taxRateBps: 1200,
  amenities: ['WIFI'],
};

function putRequest(slug: string, body: unknown) {
  return new NextRequest(`http://localhost:3000/api/console/properties/${slug}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('PUT /api/console/properties/[slug]', () => {
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

  it('returns 400 without calling upstream when taxRateBps exceeds the backend max', async () => {
    global.fetch = vi.fn();
    const response = await PUT(putRequest('driftwood-goa', { ...validBody, taxRateBps: 20000 }));
    expect(response.status).toBe(400);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('derives the slug from the URL and relays the update', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(propertyFixture), { status: 200 }));

    const response = await PUT(putRequest('driftwood-goa', validBody));

    expect(response.status).toBe(200);
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe('http://localhost:8080/api/v1/properties/driftwood-goa');
  });

  it('passes through a FRONT_DESK session\'s 403 unchanged — this route is OWNER-only on the backend, and the BFF adds no role check of its own', async () => {
    const problem = {
      type: 'https://api.altstay.com/errors/forbidden',
      title: 'Forbidden',
      status: 403,
      detail: 'Access is denied: insufficient role privileges',
    };
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(problem), { status: 403 }));

    const response = await PUT(putRequest('driftwood-goa', validBody));
    const data = await response.json();

    expect(response.status).toBe(403);
    expect(data.type).toBe(problem.type);
  });
});
