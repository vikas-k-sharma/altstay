import { proxy } from '@/lib/server/proxy';
import { GuestDtoSchema } from '@/lib/contracts/booking';

// Reads go through the BFF here too, unlike most lists — this one is fetched by a Client
// Component (the two-step guest-name search on the bookings list, phase-6 §4.4), and the browser
// never calls Spring directly.
export const GET = proxy({
  method: 'GET',
  path: '/api/v1/guests',
  responseSchema: GuestDtoSchema.array(),
});

export const POST = proxy({
  method: 'POST',
  path: '/api/v1/guests',
  requestSchema: GuestDtoSchema,
  responseSchema: GuestDtoSchema,
});
