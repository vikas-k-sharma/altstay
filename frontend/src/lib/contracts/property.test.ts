import { describe, it, expect } from 'vitest';
import { PropertyResponseSchema, UpdatePropertyRequestSchema } from './property';
import propertyFixture from './__fixtures__/property.json';

describe('PropertyResponseSchema', () => {
  it('parses a recorded PropertyResponse, including its null fields', () => {
    const parsed = PropertyResponseSchema.parse(propertyFixture);
    expect(parsed.slug).toBe('driftwood-goa');
    expect(parsed.legalName).toBeNull();
    expect(parsed.contactEmail).toBeNull();
    expect(parsed.amenities).toEqual(['WIFI', 'BREAKFAST']);
  });

  it('rejects a payload missing a required field', () => {
    const withoutTimezone: Record<string, unknown> = { ...propertyFixture };
    delete withoutTimezone.timezone;
    const parsed = PropertyResponseSchema.safeParse(withoutTimezone);
    expect(parsed.success).toBe(false);
  });
});

describe('UpdatePropertyRequestSchema', () => {
  const base = {
    name: 'Driftwood Beach Hostel',
    legalName: null,
    description: null,
    status: 'ACTIVE',
    timezone: 'Asia/Kolkata',
    currencyCode: 'INR',
    countryCode: 'IN',
    addressLine1: null,
    addressLine2: null,
    city: null,
    stateRegion: null,
    postalCode: null,
    contactEmail: null,
    contactPhone: null,
    checkInTime: '14:00:00',
    checkOutTime: '11:00:00',
    taxRateBps: 1200,
    amenities: ['WIFI'],
  };

  it('accepts a well-formed update carrying every field', () => {
    expect(UpdatePropertyRequestSchema.safeParse(base).success).toBe(true);
  });

  it('rejects taxRateBps above the backend\'s @Max(10000)', () => {
    expect(UpdatePropertyRequestSchema.safeParse({ ...base, taxRateBps: 10001 }).success).toBe(false);
  });

  it('rejects an empty name', () => {
    expect(UpdatePropertyRequestSchema.safeParse({ ...base, name: '' }).success).toBe(false);
  });
});
