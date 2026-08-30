import { describe, it, expect } from 'vitest';
import {
  BookingResponseSchema,
  TransitionRequestSchema,
  FrontDeskResponseSchema,
  CreateBookingRequestSchema,
} from './booking';
import bookingFixture from './__fixtures__/booking.json';
import frontDeskFixture from './__fixtures__/front-desk.json';

describe('BookingResponseSchema', () => {
  it('parses a recorded BookingResponse, including its null fields', () => {
    const parsed = BookingResponseSchema.parse(bookingFixture);
    expect(parsed.reference).toBe('ALT7F3K9Q');
    expect(parsed.guest.phone).toBeNull();
    expect(parsed.lines[0].spaceId).toBeNull();
    expect(parsed.allocations[0].releasedAt).toBeNull();
    expect(parsed.statusHistory[0].fromStatus).toBeNull();
  });

  it('parses a released allocation and a reasoned status change', () => {
    const released = {
      ...bookingFixture,
      allocations: [{ ...bookingFixture.allocations[0], releasedAt: '2026-09-01T09:00:00Z' }],
      statusHistory: [
        { ...bookingFixture.statusHistory[1], reason: 'Payment never cleared', toStatus: 'CANCELLED' },
      ],
    };
    const parsed = BookingResponseSchema.parse(released);
    expect(parsed.allocations[0].releasedAt).toBe('2026-09-01T09:00:00Z');
    expect(parsed.statusHistory[0].reason).toBe('Payment never cleared');
  });
});

describe('TransitionRequestSchema', () => {
  it('accepts a transition with no reason', () => {
    const parsed = TransitionRequestSchema.safeParse({ to: 'CHECKED_IN', reason: null });
    expect(parsed.success).toBe(true);
  });

  it('rejects a status outside the five legal values', () => {
    const parsed = TransitionRequestSchema.safeParse({ to: 'DELETED', reason: null });
    expect(parsed.success).toBe(false);
  });
});

describe('CreateBookingRequestSchema', () => {
  const base = {
    propertyId: '7ed13bba-74e2-4608-83c6-bedb10b9e5bd',
    propertySlug: null,
    guest: { id: null, fullName: 'New Guest', email: 'new@example.com', phone: null, countryCode: null, dateOfBirth: null, notes: null },
    checkIn: '2026-08-30',
    checkOut: '2026-09-02',
    adults: 1,
    children: 0,
    source: 'DIRECT',
    lines: [
      { roomTypeId: 'd812b0fb-cd5d-4ed7-a4d2-0a12caf6b118', spaceId: null, checkIn: null, checkOut: null, unitCount: 1, amountMinor: null },
    ],
    idempotencyKey: '8f14e45f-ceea-4c9e-8e5c-8f3e2e2b8e2a',
    notes: null,
  };

  it('accepts a well-formed CreateBookingRequest with null line dates/amount', () => {
    expect(CreateBookingRequestSchema.safeParse(base).success).toBe(true);
  });

  it('rejects a request with no lines', () => {
    expect(CreateBookingRequestSchema.safeParse({ ...base, lines: [] }).success).toBe(false);
  });
});

describe('FrontDeskResponseSchema', () => {
  it('parses a recorded FrontDeskResponse, with its summary bookings carrying empty allocations', () => {
    const parsed = FrontDeskResponseSchema.parse(frontDeskFixture);
    expect(parsed.arrivals).toHaveLength(1);
    expect(parsed.arrivals[0].allocations).toEqual([]);
    expect(parsed.arrivals[0].lines[0].roomTypeCode).toBe('MIXED-6');
  });
});
