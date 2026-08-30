import { proxy } from '@/lib/server/proxy';
import { CreateRatePlanRequestSchema, RatePlanDtoSchema } from '@/lib/contracts/rate';

export const POST = proxy({
  method: 'POST',
  path: (req) => {
    const slug = req.nextUrl.pathname.split('/').at(-2);
    return `/api/v1/properties/${slug}/rate-plans`;
  },
  requestSchema: CreateRatePlanRequestSchema,
  responseSchema: RatePlanDtoSchema,
});
