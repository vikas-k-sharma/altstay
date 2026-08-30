import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import authUserFixture from '@/lib/contracts/__fixtures__/auth-user.json';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';
import availabilityFixture from '@/lib/contracts/__fixtures__/availability.json';
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
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
}));

import NewBookingPage from './page';

describe('NewBookingPage', () => {
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

  it('starts at DATES, defaulting to the property\'s today, when nothing is in the URL', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 })) // /me
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 })); // /properties

    const element = await NewBookingPage({ searchParams: Promise.resolve({}) });
    render(element);

    expect(screen.getByLabelText('Check-in')).toHaveValue('2026-08-30');
    // No availability call made — DATES doesn't need it yet.
    expect(global.fetch).toHaveBeenCalledTimes(2);
  });

  it('pre-fills check-in from a calendar cell click\'s single `date` param, still asking for check-out', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }));

    const element = await NewBookingPage({
      searchParams: Promise.resolve({ date: '2026-09-05', roomTypeId: 'rt-1' }),
    });
    render(element);

    expect(screen.getByLabelText('Check-in')).toHaveValue('2026-09-05');
    expect(screen.getByLabelText('Check-out')).toHaveValue('2026-09-06');
  });

  it('starts at ROOM and fetches availability when from/to are both in the URL (a shared link)', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(availabilityFixture), { status: 200 }));

    const element = await NewBookingPage({
      searchParams: Promise.resolve({ from: '2026-09-01', to: '2026-09-04' }),
    });
    render(element);

    expect(screen.getByText('MIXED-6', { exact: false })).toBeInTheDocument();
    const availabilityCall = vi.mocked(global.fetch).mock.calls[2];
    const requestedUrl = new URL(availabilityCall[0] as string);
    expect(requestedUrl.searchParams.get('from')).toBe('2026-09-01');
    expect(requestedUrl.searchParams.get('to')).toBe('2026-09-04');
  });
});
