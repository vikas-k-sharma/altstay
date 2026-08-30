import { describe, it, expect, vi, afterEach } from 'vitest';
import { propertyToday, nightsBetween, formatStayRange, addDays, startOfMonth, endOfMonth } from './dates';

describe('propertyToday', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("returns the property's date, not the browser's, across a UTC-day boundary", () => {
    // 20:30 UTC on Aug 30 is already 02:00 IST on Aug 31 — the exact scenario phase-6 §13's DoD
    // names: 06:00 IST is 00:30 UTC the same day and 20:30 UTC the day before.
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-30T20:30:00Z'));

    expect(propertyToday('Asia/Kolkata')).toBe('2026-08-31');
    // Proving the naive approach this guards against would have gotten it wrong.
    expect(new Date().toISOString().slice(0, 10)).toBe('2026-08-30');
  });

  it('matches UTC when the property is in UTC', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-30T12:00:00Z'));

    expect(propertyToday('UTC')).toBe('2026-08-30');
  });
});

describe('nightsBetween', () => {
  it('counts a one-night stay as 1', () => {
    expect(nightsBetween('2026-08-30', '2026-08-31')).toBe(1);
  });

  it('counts correctly across a month boundary', () => {
    expect(nightsBetween('2026-08-30', '2026-09-02')).toBe(3);
  });
});

describe('addDays', () => {
  it('advances one day within a month', () => {
    expect(addDays('2026-08-30', 1)).toBe('2026-08-31');
  });

  it('advances across a month and year boundary', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
  });

  it('goes backward with a negative count', () => {
    expect(addDays('2026-08-01', -1)).toBe('2026-07-31');
  });
});

describe('startOfMonth / endOfMonth', () => {
  it('finds the boundaries of a 31-day month', () => {
    expect(startOfMonth('2026-08-17')).toBe('2026-08-01');
    expect(endOfMonth('2026-08-17')).toBe('2026-08-31');
  });

  it('finds the boundaries of February in a leap year', () => {
    expect(startOfMonth('2028-02-10')).toBe('2028-02-01');
    expect(endOfMonth('2028-02-10')).toBe('2028-02-29');
  });

  it('finds the boundaries of February in a non-leap year', () => {
    expect(endOfMonth('2026-02-10')).toBe('2026-02-28');
  });
});

describe('formatStayRange', () => {
  it('formats a range within the same year without repeating it', () => {
    expect(formatStayRange('2026-08-30', '2026-09-02')).toBe('Aug 30 – Sep 2, 2026');
  });

  it('shows both years when the range crosses one', () => {
    expect(formatStayRange('2026-12-30', '2027-01-02')).toBe('Dec 30, 2026 – Jan 2, 2027');
  });
});
