import { proxy } from '@/lib/server/proxy';
import { QuoteRequestSchema, QuoteResponseSchema } from '@/lib/contracts/rate';

// Allocates nothing — the wizard's REVIEW step calls this to get the same arithmetic that will
// be charged (phase-6 §4.6), never computed in the browser.
export const POST = proxy({
  method: 'POST',
  path: '/api/v1/bookings/quote',
  requestSchema: QuoteRequestSchema,
  responseSchema: QuoteResponseSchema,
});
