import { describe, it, expect } from 'vitest';
import {
  RoomTypeDtoSchema,
  SpaceDtoSchema,
  CreateRoomTypeRequestSchema,
  CreateSpaceRequestSchema,
  UpdateSpaceRequestSchema,
} from './inventory';
import roomTypeFixture from './__fixtures__/room-type.json';
import spaceFixture from './__fixtures__/space.json';

describe('RoomTypeDtoSchema', () => {
  it('parses a recorded RoomTypeDto, including its null description', () => {
    const parsed = RoomTypeDtoSchema.parse(roomTypeFixture);
    expect(parsed.saleMode).toBe('PER_UNIT');
    expect(parsed.kind).toBe('DORM');
    expect(parsed.description).toBeNull();
    expect(parsed.spaceIds).toHaveLength(1);
  });

  it('rejects a kind outside DORM/PRIVATE', () => {
    expect(RoomTypeDtoSchema.safeParse({ ...roomTypeFixture, kind: 'SUITE' }).success).toBe(false);
  });
});

describe('SpaceDtoSchema', () => {
  it('parses a recorded SpaceDto with its nested units', () => {
    const parsed = SpaceDtoSchema.parse(spaceFixture);
    expect(parsed.capacity).toBe(2);
    expect(parsed.units).toHaveLength(2);
    expect(parsed.units[0].unitKind).toBe('BUNK_TOP');
  });

  it('parses a space with zero units (the "cannot be sold" case)', () => {
    const parsed = SpaceDtoSchema.parse({ ...spaceFixture, capacity: 0, units: [] });
    expect(parsed.units).toEqual([]);
  });
});

describe('CreateRoomTypeRequestSchema', () => {
  it('rejects an unrecognised saleMode', () => {
    const parsed = CreateRoomTypeRequestSchema.safeParse({
      code: 'X', name: 'X', saleMode: 'HALF', kind: 'DORM', maxOccupancy: 1, baseRateMinor: 0, description: null, isActive: true,
    });
    expect(parsed.success).toBe(false);
  });
});

describe('CreateSpaceRequestSchema', () => {
  it('accepts a space created with an initial set of units', () => {
    const parsed = CreateSpaceRequestSchema.safeParse({
      name: '305', floor: '3', isActive: true,
      units: [{ label: 'Bed 1', unitKind: 'SINGLE', isActive: true }],
    });
    expect(parsed.success).toBe(true);
  });

  it('rejects a space with no units, matching the backend\'s own rule', () => {
    const parsed = CreateSpaceRequestSchema.safeParse({ name: '305', floor: '3', isActive: true, units: [] });
    expect(parsed.success).toBe(false);
  });
});

describe('UpdateSpaceRequestSchema', () => {
  it('accepts units: null — the "don\'t touch beds" case that a plain rename/status edit must send', () => {
    const parsed = UpdateSpaceRequestSchema.safeParse({ name: '101', floor: '1', isActive: true, units: null });
    expect(parsed.success).toBe(true);
  });

  it('still rejects an empty (non-null) units array', () => {
    const parsed = UpdateSpaceRequestSchema.safeParse({ name: '101', floor: '1', isActive: true, units: [] });
    expect(parsed.success).toBe(false);
  });
});
