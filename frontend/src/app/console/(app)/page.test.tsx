import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import authUserFixture from '@/lib/contracts/__fixtures__/auth-user.json';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';
import frontDeskFixture from '@/lib/contracts/__fixtures__/front-desk.json';
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
  useRouter: () => ({ refresh: vi.fn() }),
}));

import ConsoleTodayPage from './page';

const emptyFrontDesk = {
  propertyId: frontDeskFixture.propertyId,
  propertySlug: frontDeskFixture.propertySlug,
  date: frontDeskFixture.date,
  arrivals: [],
  departures: [],
  inHouse: [],
};

describe('ConsoleTodayPage', () => {
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

  it('renders arrivals, tonight occupancy summed across room types, and the unpaid panel', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 })) // /me
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 })) // /properties
      .mockResolvedValueOnce(new Response(JSON.stringify(frontDeskFixture), { status: 200 })) // /front-desk
      .mockResolvedValueOnce(new Response(JSON.stringify(availabilityFixture), { status: 200 })); // /availability

    const element = await ConsoleTodayPage();
    render(element);

    expect(screen.getAllByRole('link', { name: 'ALTARRIV01' }).length).toBeGreaterThan(0);
    // 4 + 0 available, 6 + 2 total across the two fixture room types.
    expect(screen.getByText('4 / 8 units available')).toBeInTheDocument();
    // paymentState is UNPAID in the fixture, so the same arrival shows in Unpaid too
    // (§12.1's documented gap: paymentState never becomes anything else in this API).
    expect(screen.getAllByText('Neha Kapoor')).toHaveLength(2);
  });

  it('shows "nothing arriving today" rather than onboarding when the tenant has other bookings', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(emptyFrontDesk), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...availabilityFixture, roomTypes: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([{ id: 'some-other-booking' }]), { status: 200 })); // /bookings (any-ever check)

    const element = await ConsoleTodayPage();
    render(element);

    expect(screen.getByText('Nothing arriving today.')).toBeInTheDocument();
    expect(screen.queryByText(/no bookings yet/i)).not.toBeInTheDocument();
  });

  it('shows the onboarding empty state when the property has never had a booking', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(emptyFrontDesk), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...availabilityFixture, roomTypes: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 })); // /bookings — none ever

    const element = await ConsoleTodayPage();
    render(element);

    expect(screen.getByText(/no bookings yet/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /inventory setup/i })).toBeInTheDocument();
  });
});
