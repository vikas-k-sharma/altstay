import { proxy } from '@/lib/server/proxy';
import { GuestDtoSchema } from '@/lib/contracts/booking';

export const PUT = proxy({
  method: 'PUT',
  path: (req) => `/api/v1/guests/${req.nextUrl.pathname.split('/').at(-1)}`,
  requestSchema: GuestDtoSchema,
  responseSchema: GuestDtoSchema,
});
