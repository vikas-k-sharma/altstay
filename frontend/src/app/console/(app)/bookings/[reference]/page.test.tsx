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
  notFound: vi.fn(() => {
    throw new Error('NEXT_NOT_FOUND');
  }),
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

import BookingDetailPage from './page';

describe('BookingDetailPage', () => {
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

  it('renders the booking sections and the legal actions for its status', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 })) // /me
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 })) // /properties
      .mockResolvedValueOnce(new Response(JSON.stringify(bookingFixture), { status: 200 })); // /bookings/:ref

    const element = await BookingDetailPage({ params: Promise.resolve({ reference: 'ALT7F3K9Q' }) });
    render(element);

    expect(screen.getByRole('heading', { name: 'ALT7F3K9Q' })).toBeInTheDocument();
    expect(screen.getByText('Bed 3', { exact: false })).toBeInTheDocument();
    // Fixture status is CHECKED_IN, whose only legal move is CHECKED_OUT/CANCELLED.
    expect(screen.getByRole('button', { name: 'Checked out' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'No-show' })).not.toBeInTheDocument();
  });

  it('calls notFound() for a reference the backend does not recognise', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 404 }));

    await expect(
      BookingDetailPage({ params: Promise.resolve({ reference: 'NOPE' }) })
    ).rejects.toThrow('NEXT_NOT_FOUND');
  });
});
