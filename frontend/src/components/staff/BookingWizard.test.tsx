import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BookingWizard } from './BookingWizard';
import availabilityFixture from '@/lib/contracts/__fixtures__/availability.json';
import quoteFixture from '@/lib/contracts/__fixtures__/quote.json';
import bookingFixture from '@/lib/contracts/__fixtures__/booking.json';
import type { PropertyAvailabilityResponse } from '@/lib/contracts/availability';

const availability = availabilityFixture as PropertyAvailabilityResponse;

const push = vi.fn();
const replace = vi.fn();
const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace, refresh }),
}));

const guests = [
  { id: 'g1', fullName: 'Arjun Mehta', email: 'arjun@example.com', phone: null, countryCode: null, dateOfBirth: null, notes: null },
];

function confirmDates() {
  fireEvent.click(screen.getByRole('button', { name: /next: room/i }));
}

function pickRoom() {
  fireEvent.click(screen.getByLabelText(/MIXED-6/));
  fireEvent.click(screen.getByRole('button', { name: /next: guest/i }));
}

function pickExistingGuest() {
  fireEvent.change(screen.getByLabelText('Search guest name'), { target: { value: 'ar' } });
  return screen.findByRole('button', { name: 'Arjun Mehta' }).then((button) => {
    fireEvent.click(button);
    fireEvent.click(screen.getByRole('button', { name: /next: review/i }));
  });
}

describe('BookingWizard', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    push.mockClear();
    replace.mockClear();
    refresh.mockClear();
    vi.stubGlobal('crypto', { randomUUID: () => 'fixed-idempotency-key' });
  });

  afterEach(() => {
    global.fetch = originalFetch;
    vi.unstubAllGlobals();
  });

  const baseProps = {
    property: { id: '7ed13bba-74e2-4608-83c6-bedb10b9e5bd' },
    today: '2026-08-30',
    initialCheckIn: '2026-08-30',
    initialCheckOut: '2026-08-31',
    initialRoomTypeId: null,
    availability,
    startAtRoom: true,
  };

  it('walks DATES → ROOM → GUEST → REVIEW → CREATED, updating the URL after dates and quoting before confirming', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(guests), { status: 200 })) // guest search
      .mockResolvedValueOnce(new Response(JSON.stringify(quoteFixture), { status: 200 })) // quote
      .mockResolvedValueOnce(new Response(JSON.stringify(bookingFixture), { status: 201 })); // create

    render(<BookingWizard {...baseProps} startAtRoom={false} />);

    confirmDates();
    expect(replace).toHaveBeenCalledWith('/console/bookings/new?from=2026-08-30&to=2026-08-31');
    pickRoom();

    await pickExistingGuest();

    expect(await screen.findByText(/₹2,184.00/)).toBeInTheDocument(); // quote total
    fireEvent.click(screen.getByRole('button', { name: /confirm booking/i }));

    expect(await screen.findByText(/booking created/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /ALT7F3K9Q/ })).toHaveAttribute(
      'href',
      '/console/bookings/ALT7F3K9Q'
    );

    const bookingCallBody = JSON.parse((vi.mocked(global.fetch).mock.calls[2][1] as RequestInit).body as string);
    expect(bookingCallBody.idempotencyKey).toBe('fixed-idempotency-key');
    expect(bookingCallBody.guest).toEqual(guests[0]);
  });

  it('returns to ROOM and refreshes on a 409 at confirm, without losing the guest already picked', async () => {
    const problem = {
      type: 'https://api.altstay.com/errors/no-availability',
      title: 'No Availability',
      status: 409,
      detail: 'That bed just went.',
    };
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(guests), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(quoteFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(problem), { status: 409 }));

    render(<BookingWizard {...baseProps} />);

    pickRoom();
    await pickExistingGuest();
    await screen.findByText(/₹2,184.00/);
    fireEvent.click(screen.getByRole('button', { name: /confirm booking/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('That bed just went.');
    expect(refresh).toHaveBeenCalled();
    // Back at ROOM, not stuck on REVIEW with a stale quote.
    expect(screen.getByRole('button', { name: /next: guest/i })).toBeInTheDocument();
  });

  it('reuses the same idempotencyKey on a retry after a non-409 failure', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(guests), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(quoteFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ detail: 'Model is briefly unavailable' }), { status: 502 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(bookingFixture), { status: 201 }));

    render(<BookingWizard {...baseProps} />);

    pickRoom();
    await pickExistingGuest();
    await screen.findByText(/₹2,184.00/);

    fireEvent.click(screen.getByRole('button', { name: /confirm booking/i }));
    await screen.findByRole('alert');
    fireEvent.click(screen.getByRole('button', { name: /confirm booking/i }));
    await waitFor(() => expect(screen.getByText(/booking created/i)).toBeInTheDocument());

    const firstAttemptBody = JSON.parse((vi.mocked(global.fetch).mock.calls[2][1] as RequestInit).body as string);
    const secondAttemptBody = JSON.parse((vi.mocked(global.fetch).mock.calls[3][1] as RequestInit).body as string);
    expect(firstAttemptBody.idempotencyKey).toBe(secondAttemptBody.idempotencyKey);
  });
});
