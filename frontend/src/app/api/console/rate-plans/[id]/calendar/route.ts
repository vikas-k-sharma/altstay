import { proxy } from '@/lib/server/proxy';
import { SetRateCalendarRequestSchema } from '@/lib/contracts/rate';

// One request for a whole range, expanded server-side — never one request per date (phase-6 §4.9).
export const PUT = proxy({
  method: 'PUT',
  path: (req) => `/api/v1/rate-plans/${req.nextUrl.pathname.split('/').at(-2)}/calendar`,
  requestSchema: SetRateCalendarRequestSchema,
});
