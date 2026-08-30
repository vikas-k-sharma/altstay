import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
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
}));

import CalendarPage from './page';

describe('CalendarPage', () => {
  const originalFetch = global.fetch;
  const originalBackendUrl = process.env.BACKEND_URL;

  beforeEach(() => {
    cookieStore.clear();
    cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
    process.env.BACKEND_URL = 'http://localhost:8080';
    // Fixed so propertyToday(property.timezone) — Asia/Kolkata in the fixture — is deterministic.
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-30T06:00:00Z'));
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

  it('requests availability for the default 14-day window and renders the grid plus legend', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 })) // /me
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 })) // /properties
      .mockResolvedValueOnce(new Response(JSON.stringify(availabilityFixture), { status: 200 })); // /availability

    const element = await CalendarPage({ searchParams: Promise.resolve({}) });
    render(element);

    const availabilityCall = vi.mocked(global.fetch).mock.calls[2];
    const requestedUrl = new URL(availabilityCall[0] as string);
    expect(requestedUrl.searchParams.get('from')).toBe('2026-08-30');
    expect(requestedUrl.searchParams.get('to')).toBe('2026-09-13'); // +14 days
    expect(requestedUrl.searchParams.has('roomTypeId')).toBe(false);

    const table = screen.getByRole('table');
    expect(within(table).getByText('MIXED-6')).toBeInTheDocument();
    expect(within(table).getByText('PRIVATE-DOUBLE')).toBeInTheDocument();
    expect(screen.getByText(/product working as designed/i)).toBeInTheDocument();
  });

  it('clamps an out-of-range days value to 60 rather than sending it through', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(availabilityFixture), { status: 200 }));

    const element = await CalendarPage({
      searchParams: Promise.resolve({ from: '2026-08-30', days: '400' }),
    });
    render(element);

    const availabilityCall = vi.mocked(global.fetch).mock.calls[2];
    const requestedUrl = new URL(availabilityCall[0] as string);
    expect(requestedUrl.searchParams.get('to')).toBe('2026-10-29'); // +60 days, clamped
  });

  it('filters the displayed room types by roomTypeId without sending it to the backend', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(availabilityFixture), { status: 200 }));

    const element = await CalendarPage({
      searchParams: Promise.resolve({ roomTypeId: availabilityFixture.roomTypes[0].roomTypeId }),
    });
    render(element);

    const table = screen.getByRole('table');
    expect(within(table).getByText('MIXED-6')).toBeInTheDocument();
    expect(within(table).queryByText('PRIVATE-DOUBLE')).not.toBeInTheDocument();

    const availabilityCall = vi.mocked(global.fetch).mock.calls[2];
    const requestedUrl = new URL(availabilityCall[0] as string);
    expect(requestedUrl.searchParams.has('roomTypeId')).toBe(false);
  });

  it('shows a setup link instead of an empty grid when there are no active room types', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ ...availabilityFixture, roomTypes: [] }), { status: 200 })
      );

    const element = await CalendarPage({ searchParams: Promise.resolve({}) });
    render(element);

    expect(screen.getByText(/no active room types yet/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /set up inventory/i })).toBeInTheDocument();
  });
});
