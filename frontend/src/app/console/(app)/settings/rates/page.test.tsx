import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import authUserFixture from '@/lib/contracts/__fixtures__/auth-user.json';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';
import roomTypeFixture from '@/lib/contracts/__fixtures__/room-type.json';
import ratePlanFixture from '@/lib/contracts/__fixtures__/rate-plan.json';
import { SESSION_COOKIE_NAME } from '@/lib/server/session';

const cookieStore = new Map<string, { value: string }>();

vi.mock('next/headers', () => ({
  cookies: vi.fn(async () => ({
    get: (name: string) => cookieStore.get(name),
  })),
}));

vi.mock('next/navigation', () => ({
  redirect: vi.fn((path: string) => {
    throw new Error(`NEXT_REDIRECT:${path}`);
  }),
  useRouter: () => ({ refresh: vi.fn() }),
}));

import RatesSettingsPage from './page';

describe('RatesSettingsPage', () => {
  const originalFetch = global.fetch;
  const originalBackendUrl = process.env.BACKEND_URL;

  beforeEach(() => {
    cookieStore.clear();
    cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
    process.env.BACKEND_URL = 'http://localhost:8080';
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-30T06:00:00Z')); // property is Asia/Kolkata
  });

  afterEach(() => {
    global.fetch = originalFetch;
    if (originalBackendUrl !== undefined) {
      process.env.BACKEND_URL = originalBackendUrl;
    } else {
      delete process.env.BACKEND_URL;
    }
    vi.useRealTimers();
  });

  it('defaults to the current month and the first rate plan, fetching its calendar', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...authUserFixture, roles: ['MANAGER'] }), { status: 200 })) // /me
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 })) // /properties
      .mockResolvedValueOnce(new Response(JSON.stringify([roomTypeFixture]), { status: 200 })) // /room-types
      .mockResolvedValueOnce(new Response(JSON.stringify([ratePlanFixture]), { status: 200 })) // /rate-plans
      .mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 })); // /calendar

    const element = await RatesSettingsPage({ searchParams: Promise.resolve({}) });
    render(element);

    const calendarCall = vi.mocked(global.fetch).mock.calls[4];
    const requestedUrl = new URL(calendarCall[0] as string);
    expect(requestedUrl.pathname).toBe(`/api/v1/rate-plans/${ratePlanFixture.id}/calendar`);
    expect(requestedUrl.searchParams.get('from')).toBe('2026-08-01');
    expect(requestedUrl.searchParams.get('to')).toBe('2026-08-31');

    expect(screen.getByRole('heading', { name: /MIXED-6 · Standard rate/ })).toBeInTheDocument();
  });

  it('does not fetch a calendar when the property has no rate plans yet', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...authUserFixture, roles: ['MANAGER'] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([roomTypeFixture]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 })); // no rate plans

    const element = await RatesSettingsPage({ searchParams: Promise.resolve({}) });
    render(element);

    expect(global.fetch).toHaveBeenCalledTimes(4);
    expect(screen.getByText(/create a rate plan/i)).toBeInTheDocument();
  });

  it('redirects a FRONT_DESK session to /console', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify({ ...authUserFixture, roles: ['FRONT_DESK'] }), { status: 200 })
    );

    await expect(RatesSettingsPage({ searchParams: Promise.resolve({}) })).rejects.toThrow(
      'NEXT_REDIRECT:/console'
    );
  });
});
