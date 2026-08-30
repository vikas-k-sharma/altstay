import { describe, it, expect } from 'vitest';
import { PropertyAvailabilityResponseSchema } from './availability';
import availabilityFixture from './__fixtures__/availability.json';

describe('PropertyAvailabilityResponseSchema', () => {
  it('parses a recorded PropertyAvailabilityResponse', () => {
    const parsed = PropertyAvailabilityResponseSchema.parse(availabilityFixture);
    expect(parsed.roomTypes).toHaveLength(2);
    expect(parsed.roomTypes[0].saleMode).toBe('PER_UNIT');
  });

  it('distinguishes bookableWholeSpaces (range-wide) from days[].availableSpaces (per-day)', () => {
    const parsed = PropertyAvailabilityResponseSchema.parse(availabilityFixture);
    const wholeType = parsed.roomTypes[1];
    expect(wholeType.saleMode).toBe('WHOLE');
    expect(wholeType.bookableWholeSpaces).toBe(1);
    expect(wholeType.days[0].availableSpaces).toBe(1);
  });

  it('rejects a saleMode outside PER_UNIT/WHOLE', () => {
    const invalid = {
      ...availabilityFixture,
      roomTypes: [{ ...availabilityFixture.roomTypes[0], saleMode: 'HALF' }],
    };
    expect(PropertyAvailabilityResponseSchema.safeParse(invalid).success).toBe(false);
  });
});
