import { proxy } from '@/lib/server/proxy';
import { CreateBookingRequestSchema, BookingResponseSchema } from '@/lib/contracts/booking';

export const POST = proxy({
  method: 'POST',
  path: '/api/v1/bookings',
  requestSchema: CreateBookingRequestSchema,
  responseSchema: BookingResponseSchema,
});
