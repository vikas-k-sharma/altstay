import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { PUT } from './route';

const ratePlanId = '9a450948-2937-4075-8732-6c57ed89cfda';
const validBody = { from: '2026-12-24', to: '2026-12-26', amountMinor: 120000 };

function putRequest(id: string, body: unknown) {
  return new NextRequest(`http://localhost:3000/api/console/rate-plans/${id}/calendar`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('PUT /api/console/rate-plans/[id]/calendar', () => {
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

  it('returns 400 without calling upstream when to is not a valid date', async () => {
    global.fetch = vi.fn();
    const response = await PUT(putRequest(ratePlanId, { ...validBody, to: 'not-a-date' }));
    expect(response.status).toBe(400);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('derives the rate plan id from the URL and sends one request for the whole range', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 }));

    const response = await PUT(putRequest(ratePlanId, validBody));

    expect(response.status).toBe(204);
    const [url, init] = vi.mocked(global.fetch).mock.calls[0];
    expect(url).toBe(`http://localhost:8080/api/v1/rate-plans/${ratePlanId}/calendar`);
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual(validBody);
  });
});
