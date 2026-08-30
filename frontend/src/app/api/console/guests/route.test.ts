import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { GET, POST } from './route';

const guestFixture = {
  id: 'c7f72790-b9d3-405e-abbc-748a7ed7ccf9',
  fullName: 'Arjun Mehta',
  email: 'arjun@example.com',
  phone: null,
  countryCode: 'IN',
  dateOfBirth: null,
  notes: null,
};

function getRequest() {
  return new NextRequest('http://localhost:3000/api/console/guests');
}

function postRequest(body: unknown) {
  return new NextRequest('http://localhost:3000/api/console/guests', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('/api/console/guests', () => {
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

  it('GET relays the tenant guest list', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify([guestFixture]), { status: 200 }));

    const response = await GET(getRequest());
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data).toEqual([guestFixture]);
  });

  it('POST returns 400 without calling upstream when fullName is missing', async () => {
    global.fetch = vi.fn();

    const response = await POST(postRequest({ ...guestFixture, id: null, fullName: '' }));

    expect(response.status).toBe(400);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('POST creates a guest and relays the 201', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(guestFixture), { status: 201 }));

    const response = await POST(postRequest({ ...guestFixture, id: null }));
    const data = await response.json();

    expect(response.status).toBe(201);
    expect(data.fullName).toBe('Arjun Mehta');
  });
});
