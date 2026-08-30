import { describe, it, expect } from 'vitest';
import { legalTransitionsFrom, statusLabel, type BookingStatusValue } from './bookingStatus';

// Mirrors BookingStatus.canTransitionTo in the backend, mutation-tested there against the same
// five cases (backend/src/main/java/com/altstay/api/booking/BookingStatus.java).
describe('legalTransitionsFrom', () => {
  it.each<[BookingStatusValue, BookingStatusValue[]]>([
    ['BOOKED', ['CHECKED_IN', 'CANCELLED', 'NO_SHOW']],
    ['CHECKED_IN', ['CHECKED_OUT', 'CANCELLED']],
    ['CHECKED_OUT', []],
    ['CANCELLED', []],
    ['NO_SHOW', []],
  ])('%s -> %j', (status, expected) => {
    expect(legalTransitionsFrom(status)).toEqual(expected);
  });

  it('every status has a display label', () => {
    const statuses: BookingStatusValue[] = ['BOOKED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW'];
    for (const status of statuses) {
      expect(statusLabel(status)).toBeTruthy();
    }
  });
});
