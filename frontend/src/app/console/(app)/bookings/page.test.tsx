import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import authUserFixture from '@/lib/contracts/__fixtures__/auth-user.json';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';
import bookingFixture from '@/lib/contracts/__fixtures__/booking.json';
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
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => '/console/bookings',
  useSearchParams: () => new URLSearchParams(),
}));

import BookingsPage from './page';

describe('BookingsPage', () => {
  const originalFetch = global.fetch;
  const originalBackendUrl = process.env.BACKEND_URL;

  beforeEach(() => {
    cookieStore.clear();
    cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
    process.env.BACKEND_URL = 'http://localhost:8080';
  });

  afterEach(() => {
    global.fetch = originalFetch;
    if (originalBackendUrl !== undefined) {
      process.env.BACKEND_URL = originalBackendUrl;
    } else {
      delete process.env.BACKEND_URL;
    }
  });

  it('lists bookings with the filters passed as query params to the backend', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 })) // /me
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 })) // /properties
      .mockResolvedValueOnce(new Response(JSON.stringify([bookingFixture]), { status: 200 })); // /bookings

    const element = await BookingsPage({ searchParams: Promise.resolve({ status: 'CHECKED_IN' }) });
    render(element);

    expect(screen.getByRole('link', { name: 'ALT7F3K9Q' })).toBeInTheDocument();
    expect(screen.getByText('Arjun Mehta')).toBeInTheDocument();

    const bookingsCall = vi.mocked(global.fetch).mock.calls[2];
    const requestedUrl = new URL(bookingsCall[0] as string);
    expect(requestedUrl.searchParams.get('propertyId')).toBe(propertyFixture.id);
    expect(requestedUrl.searchParams.get('status')).toBe('CHECKED_IN');
  });

  it('shows an empty state when no bookings match the filters', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 }));

    const element = await BookingsPage({ searchParams: Promise.resolve({}) });
    render(element);

    expect(screen.getByText(/no bookings match/i)).toBeInTheDocument();
  });
});
