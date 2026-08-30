import { proxy } from '@/lib/server/proxy';
import { TransitionRequestSchema, BookingResponseSchema } from '@/lib/contracts/booking';

// The reference is read off the URL rather than threaded through as a route param — proxy()'s
// handler signature is (req) only, so every dynamic BFF route resolves its path this way.
export const POST = proxy({
  method: 'POST',
  path: (req) => {
    const reference = req.nextUrl.pathname.split('/').at(-2);
    return `/api/v1/bookings/${reference}/transitions`;
  },
  requestSchema: TransitionRequestSchema,
  responseSchema: BookingResponseSchema,
});
