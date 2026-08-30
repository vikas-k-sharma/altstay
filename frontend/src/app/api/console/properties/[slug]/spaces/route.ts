import { proxy } from '@/lib/server/proxy';
import { CreateSpaceRequestSchema, SpaceDtoSchema } from '@/lib/contracts/inventory';

export const POST = proxy({
  method: 'POST',
  path: (req) => {
    const slug = req.nextUrl.pathname.split('/').at(-2);
    return `/api/v1/properties/${slug}/spaces`;
  },
  requestSchema: CreateSpaceRequestSchema,
  responseSchema: SpaceDtoSchema,
});
