import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { POST, DELETE } from './route';

const roomTypeId = 'd812b0fb-cd5d-4ed7-a4d2-0a12caf6b118';
const spaceId = 'c7fcafeb-695e-4a53-b221-a8933baea200';

function mappingRequest(method: string) {
  return new NextRequest(`http://localhost:3000/api/console/room-types/${roomTypeId}/spaces/${spaceId}`, {
    method,
  });
}

describe('/api/console/room-types/[id]/spaces/[spaceId]', () => {
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

  it('POST associates the space with the room type', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 201 }));

    const response = await POST(mappingRequest('POST'));

    expect(response.status).toBe(201);
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe(
      `http://localhost:8080/api/v1/room-types/${roomTypeId}/spaces/${spaceId}`
    );
  });

  it('DELETE dissociates the space from the room type', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 }));

    const response = await DELETE(mappingRequest('DELETE'));

    expect(response.status).toBe(204);
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe(
      `http://localhost:8080/api/v1/room-types/${roomTypeId}/spaces/${spaceId}`
    );
  });
});
