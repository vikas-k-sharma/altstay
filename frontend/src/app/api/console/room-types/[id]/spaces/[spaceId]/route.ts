import { proxy } from '@/lib/server/proxy';

// The hybrid mapping (phase-6 §4.8). Neither direction has a request body — the URL is the
// whole request — and both succeed with no content, matching the upstream 201/204.
function mappingPath(req: { nextUrl: { pathname: string } }): string {
  const segments = req.nextUrl.pathname.split('/');
  const [id, spaceId] = [segments.at(-3), segments.at(-1)];
  return `/api/v1/room-types/${id}/spaces/${spaceId}`;
}

export const POST = proxy({ method: 'POST', path: mappingPath });
export const DELETE = proxy({ method: 'DELETE', path: mappingPath });
