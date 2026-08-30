import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { consoleFetch } from './clientFetch';

describe('consoleFetch', () => {
  const originalFetch = global.fetch;
  const originalLocation = window.location;

  beforeEach(() => {
    // jsdom's real navigation throws "not implemented"; stub assign so we can observe the call.
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...originalLocation, pathname: '/console/bookings', search: '?status=BOOKED', assign: vi.fn() },
    });
  });

  afterEach(() => {
    global.fetch = originalFetch;
    Object.defineProperty(window, 'location', { configurable: true, value: originalLocation });
  });

  it('passes a successful response through untouched', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), { status: 200 }));

    const response = await consoleFetch('/api/console/bookings');

    expect(response.status).toBe(200);
    expect(window.location.assign).not.toHaveBeenCalled();
  });

  it('redirects to login with the current path as next= on a 401', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 401 }));

    await consoleFetch('/api/console/bookings');

    expect(window.location.assign).toHaveBeenCalledWith(
      '/console/login?next=%2Fconsole%2Fbookings%3Fstatus%3DBOOKED'
    );
  });
});
