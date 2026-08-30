import { z } from 'zod';

// Mirrors com.altstay.api.rate.RateService / QuoteCalculator. No nullable fields anywhere here —
// every field is a primitive or non-null UUID/String in the Java records.

// One plan per room type is marked default; the database enforces at most one
// (`rate_plan_one_default_per_room_type`) — creating a second default for the same room type is a
// real 409 the settings screen has to render, not something the console prevents client-side.
export const RatePlanDtoSchema = z.object({
  id: z.string().uuid(),
  tenantId: z.string().uuid(),
  propertyId: z.string().uuid(),
  roomTypeId: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  isDefault: z.boolean(),
  isActive: z.boolean(),
});
export type RatePlanDto = z.infer<typeof RatePlanDtoSchema>;

export const CreateRatePlanRequestSchema = z.object({
  roomTypeId: z.string().uuid(),
  code: z.string().min(1, 'code is required'),
  name: z.string().min(1, 'name is required'),
  isDefault: z.boolean(),
});
export type CreateRatePlanRequest = z.infer<typeof CreateRatePlanRequestSchema>;

// stayDate is inclusive on both ends here — `setCalendarRange` loops `!d.isAfter(to)`, unlike the
// half-open `[checkIn, checkOut)` convention every booking date range uses. Worth restating at
// every call site that touches this range, since it is the one place in the console where "to"
// means something different from everywhere else.
export const RateCalendarDtoSchema = z.object({
  stayDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  amountMinor: z.number().int(),
});
export type RateCalendarDto = z.infer<typeof RateCalendarDtoSchema>;

export const SetRateCalendarRequestSchema = z.object({
  from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  amountMinor: z.number().int().min(0),
});
export type SetRateCalendarRequest = z.infer<typeof SetRateCalendarRequestSchema>;

export const NightlyRateSchema = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  rateMinor: z.number().int(),
});
export type NightlyRate = z.infer<typeof NightlyRateSchema>;

export const QuoteRequestSchema = z.object({
  propertyId: z.string().uuid().nullable().optional(),
  propertySlug: z.string().nullable().optional(),
  roomTypeId: z.string().uuid(),
  ratePlanId: z.string().uuid().nullable().optional(),
  checkIn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  checkOut: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  unitCount: z.number().int().min(1),
});
export type QuoteRequest = z.infer<typeof QuoteRequestSchema>;

// It allocates nothing and returns the same arithmetic that will be charged (phase-5 §6) — the
// wizard never sums nightlyRates itself, since that can disagree with the backend on a rounding
// boundary (phase-6 §4.6).
export const QuoteResponseSchema = z.object({
  subtotalMinor: z.number().int(),
  taxMinor: z.number().int(),
  totalMinor: z.number().int(),
  currencyCode: z.string().length(3),
  nightlyRates: z.array(NightlyRateSchema),
});
export type QuoteResponse = z.infer<typeof QuoteResponseSchema>;
