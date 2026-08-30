import { proxy } from '@/lib/server/proxy';
import { UpdateSpaceRequestSchema, SpaceDtoSchema } from '@/lib/contracts/inventory';

export const PUT = proxy({
  method: 'PUT',
  path: (req) => {
    const segments = req.nextUrl.pathname.split('/');
    const [slug, id] = [segments.at(-3), segments.at(-1)];
    return `/api/v1/properties/${slug}/spaces/${id}`;
  },
  requestSchema: UpdateSpaceRequestSchema,
  responseSchema: SpaceDtoSchema,
});
