import { proxy } from '@/lib/server/proxy';
import { UpdatePropertyRequestSchema, PropertyResponseSchema } from '@/lib/contracts/property';

export const PUT = proxy({
  method: 'PUT',
  path: (req) => `/api/v1/properties/${req.nextUrl.pathname.split('/').at(-1)}`,
  requestSchema: UpdatePropertyRequestSchema,
  responseSchema: PropertyResponseSchema,
});
