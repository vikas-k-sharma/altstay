import { describe, it, expect } from 'vitest';
import { AmenityResponseSchema } from './amenity';
import amenitiesFixture from './__fixtures__/amenities.json';

describe('AmenityResponseSchema', () => {
  it('parses a recorded amenity list, grouped by category', () => {
    const parsed = AmenityResponseSchema.array().parse(amenitiesFixture);
    expect(parsed).toHaveLength(3);
    expect(parsed[0].category).toBe('Connectivity');
  });
});
