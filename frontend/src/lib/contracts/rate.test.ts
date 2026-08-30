import { describe, it, expect } from 'vitest';
import {
  QuoteRequestSchema,
  QuoteResponseSchema,
  RatePlanDtoSchema,
  RateCalendarDtoSchema,
  SetRateCalendarRequestSchema,
} from './rate';
import quoteFixture from './__fixtures__/quote.json';
import ratePlanFixture from './__fixtures__/rate-plan.json';

describe('QuoteResponseSchema', () => {
  it('parses a recorded QuoteResponse with its per-night breakdown', () => {
    const parsed = QuoteResponseSchema.parse(quoteFixture);
    expect(parsed.nightlyRates).toHaveLength(3);
    expect(parsed.totalMinor).toBe(218400);
  });
});

describe('QuoteRequestSchema', () => {
  it('accepts a request with no rate plan override', () => {
    const parsed = QuoteRequestSchema.safeParse({
      propertyId: '7ed13bba-74e2-4608-83c6-bedb10b9e5bd',
      roomTypeId: 'd812b0fb-cd5d-4ed7-a4d2-0a12caf6b118',
      checkIn: '2026-08-30',
      checkOut: '2026-09-02',
      unitCount: 1,
    });
    expect(parsed.success).toBe(true);
  });

  it('rejects a unitCount below 1', () => {
    const parsed = QuoteRequestSchema.safeParse({
      propertyId: '7ed13bba-74e2-4608-83c6-bedb10b9e5bd',
      roomTypeId: 'd812b0fb-cd5d-4ed7-a4d2-0a12caf6b118',
      checkIn: '2026-08-30',
      checkOut: '2026-09-02',
      unitCount: 0,
    });
    expect(parsed.success).toBe(false);
  });
});

describe('RatePlanDtoSchema', () => {
  it('parses a recorded RatePlanDto', () => {
    const parsed = RatePlanDtoSchema.parse(ratePlanFixture);
    expect(parsed.isDefault).toBe(true);
    expect(parsed.code).toBe('STANDARD');
  });
});

describe('RateCalendarDtoSchema', () => {
  it('parses a stay-date override', () => {
    const parsed = RateCalendarDtoSchema.parse({ stayDate: '2026-12-31', amountMinor: 90000 });
    expect(parsed.amountMinor).toBe(90000);
  });
});

describe('SetRateCalendarRequestSchema', () => {
  it('accepts an inclusive from/to range — unlike a booking range, "to" is included', () => {
    const parsed = SetRateCalendarRequestSchema.safeParse({
      from: '2026-12-24',
      to: '2026-12-26',
      amountMinor: 120000,
    });
    expect(parsed.success).toBe(true);
  });
});
