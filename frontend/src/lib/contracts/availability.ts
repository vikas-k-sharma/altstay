import { z } from 'zod';

// Mirrors the nested records in com.altstay.api.inventory.AvailabilityService. No nullable
// fields here — every one is a primitive or a non-null String/List in the Java source.

export const DayAvailabilityDtoSchema = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  availableUnits: z.number().int(),
  totalUnits: z.number().int(),
  availableSpaces: z.number().int(),
  totalSpaces: z.number().int(),
  rateMinor: z.number().int(),
});
export type DayAvailabilityDto = z.infer<typeof DayAvailabilityDtoSchema>;

// `bookableWholeSpaces` is range-wide (spaces free across the *entire* requested range); `days[].
// availableSpaces` is per-day. The calendar (§4.3) renders the per-day count; anything deciding
// whether a WHOLE room type can be sold across a multi-night stay must use bookableWholeSpaces —
// using the per-day count there would offer a room free on 3 of 4 nights.
export const RoomTypeAvailabilityDtoSchema = z.object({
  roomTypeId: z.string().uuid(),
  code: z.string(),
  saleMode: z.enum(['PER_UNIT', 'WHOLE']),
  bookableWholeSpaces: z.number().int(),
  days: z.array(DayAvailabilityDtoSchema),
});
export type RoomTypeAvailabilityDto = z.infer<typeof RoomTypeAvailabilityDtoSchema>;

export const PropertyAvailabilityResponseSchema = z.object({
  from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  currency: z.string().length(3),
  roomTypes: z.array(RoomTypeAvailabilityDtoSchema),
});
export type PropertyAvailabilityResponse = z.infer<typeof PropertyAvailabilityResponseSchema>;
