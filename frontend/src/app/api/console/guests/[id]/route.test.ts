import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { PUT } from './route';

const guestFixture = {
  id: 'c7f72790-b9d3-405e-abbc-748a7ed7ccf9',
  fullName: 'Arjun Mehta',
  email: 'arjun@example.com',
  phone: '+91 9000000000',
  countryCode: 'IN',
  dateOfBirth: null,
  notes: null,
};

function putRequest(id: string, body: unknown) {
  return new NextRequest(`http://localhost:3000/api/console/guests/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('PUT /api/console/guests/[id]', () => {
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

  it('derives the guest id from the URL', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(guestFixture), { status: 200 }));

    const response = await PUT(putRequest(guestFixture.id, guestFixture));

    expect(response.status).toBe(200);
    const [url] = vi.mocked(global.fetch).mock.calls[0];
    expect(url).toBe(`http://localhost:8080/api/v1/guests/${guestFixture.id}`);
  });

  it('passes through a 404 for an unknown guest', async () => {
    const problem = {
      type: 'https://api.altstay.com/errors/not-found',
      title: 'Not Found',
      status: 404,
      detail: 'Guest not found',
    };
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(problem), { status: 404 }));

    const response = await PUT(putRequest('00000000-0000-4000-8000-000000000000', guestFixture));
    expect(response.status).toBe(404);
  });
});
