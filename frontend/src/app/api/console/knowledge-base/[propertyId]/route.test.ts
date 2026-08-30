import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { POST } from './route';
import versionFixture from '@/lib/contracts/__fixtures__/knowledge-base-version.json';

const propertyId = '7ed13bba-74e2-4608-83c6-bedb10b9e5bd';

function postRequest(id: string, body: unknown) {
  return new NextRequest(`http://localhost:3000/api/console/knowledge-base/${id}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('POST /api/console/knowledge-base/[propertyId]', () => {
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

  it('returns 400 without calling upstream on content over 20,000 characters', async () => {
    global.fetch = vi.fn();
    const response = await POST(postRequest(propertyId, { content: 'x'.repeat(20_001) }));
    expect(response.status).toBe(400);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('derives the property id (not slug) from the URL and saves', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(versionFixture), { status: 200 }));

    const response = await POST(postRequest(propertyId, { content: 'Check-in is 2 PM.' }));

    expect(response.status).toBe(200);
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe(
      `http://localhost:8080/api/v1/properties/${propertyId}/knowledge-base`
    );
  });

  it('passes through a 409 knowledge-base-conflict unchanged', async () => {
    const problem = {
      type: 'https://api.altstay.com/errors/knowledge-base-conflict',
      title: 'Knowledge Base Conflict',
      status: 409,
      detail: 'Someone else saved first',
    };
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(problem), { status: 409 }));

    const response = await POST(postRequest(propertyId, { content: 'Check-in is 2 PM.' }));
    const data = await response.json();

    expect(response.status).toBe(409);
    expect(data.type).toBe(problem.type);
  });
});
