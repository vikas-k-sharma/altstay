import { describe, it, expect } from 'vitest';
import { bookingWizardReducer, initialWizardState, type WizardState } from './bookingWizardReducer';
import type { GuestDto } from '@/lib/contracts/booking';

const guest: GuestDto = {
  id: null,
  fullName: 'Arjun Mehta',
  email: 'arjun@example.com',
  phone: null,
  countryCode: null,
  dateOfBirth: null,
  notes: null,
};

function baseState(overrides: Partial<WizardState> = {}): WizardState {
  return {
    ...initialWizardState({
      checkIn: '2026-08-30',
      checkOut: '2026-08-31',
      roomTypeId: null,
      availability: null,
      startAtRoom: false,
    }),
    ...overrides,
  };
}

describe('bookingWizardReducer', () => {
  it('starts at DATES by default, or ROOM when seeded from a shared link', () => {
    expect(initialWizardState({ checkIn: 'x', checkOut: 'y', roomTypeId: null, availability: null, startAtRoom: false }).step).toBe('DATES');
    expect(initialWizardState({ checkIn: 'x', checkOut: 'y', roomTypeId: null, availability: null, startAtRoom: true }).step).toBe('ROOM');
  });

  it('DATES_CONFIRMED advances to ROOM and clears any prior room selection', () => {
    const state = baseState({ roomTypeId: 'rt-1', quote: { subtotalMinor: 1, taxMinor: 0, totalMinor: 1, currencyCode: 'INR', nightlyRates: [] } });
    const next = bookingWizardReducer(state, {
      type: 'DATES_CONFIRMED',
      checkIn: '2026-09-01',
      checkOut: '2026-09-03',
      adults: 2,
      children: 1,
    });
    expect(next.step).toBe('ROOM');
    expect(next.checkIn).toBe('2026-09-01');
    expect(next.adults).toBe(2);
    expect(next.roomTypeId).toBeNull();
    expect(next.quote).toBeNull();
  });

  it('ROOM_CONFIRMED advances to GUEST', () => {
    const next = bookingWizardReducer(baseState({ step: 'ROOM' }), {
      type: 'ROOM_CONFIRMED',
      roomTypeId: 'rt-1',
      unitCount: 2,
    });
    expect(next.step).toBe('GUEST');
    expect(next.roomTypeId).toBe('rt-1');
    expect(next.unitCount).toBe(2);
  });

  it('GUEST_CONFIRMED advances to REVIEW carrying the given idempotencyKey', () => {
    const next = bookingWizardReducer(baseState({ step: 'GUEST' }), {
      type: 'GUEST_CONFIRMED',
      guest,
      idempotencyKey: 'key-1',
    });
    expect(next.step).toBe('REVIEW');
    expect(next.guest).toEqual(guest);
    expect(next.idempotencyKey).toBe('key-1');
  });

  it('CONFLICT returns to ROOM, discards the quote, and sets the error', () => {
    const next = bookingWizardReducer(
      baseState({ step: 'REVIEW', quote: { subtotalMinor: 1, taxMinor: 0, totalMinor: 1, currencyCode: 'INR', nightlyRates: [] } }),
      { type: 'CONFLICT', message: 'That bed just went.' }
    );
    expect(next.step).toBe('ROOM');
    expect(next.quote).toBeNull();
    expect(next.error).toBe('That bed just went.');
  });

  it('BACK returns to the given step and clears the error', () => {
    const next = bookingWizardReducer(baseState({ step: 'REVIEW', error: 'oops' }), { type: 'BACK', to: 'ROOM' });
    expect(next.step).toBe('ROOM');
    expect(next.error).toBeNull();
  });

  it('BOOKING_CREATED moves to CREATED with the reference', () => {
    const next = bookingWizardReducer(baseState({ step: 'REVIEW' }), {
      type: 'BOOKING_CREATED',
      reference: 'ALT7F3K9Q',
    });
    expect(next.step).toBe('CREATED');
    expect(next.createdBooking).toEqual({ reference: 'ALT7F3K9Q' });
  });
});
