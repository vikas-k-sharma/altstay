import { z } from 'zod';

// Mirrors com.altstay.api.property.PropertyController.PropertyResponse. Twenty-two fields;
// `id` and `tenantId` are UUIDs, `checkInTime`/`checkOutTime` are LocalTime, `createdAt` is
// OffsetDateTime, `amenities` is a flat list of amenity codes.
export const PropertyResponseSchema = z.object({
  id: z.string().uuid(),
  tenantId: z.string().uuid(),
  name: z.string(),
  slug: z.string(),
  legalName: z.string().nullable(),
  description: z.string().nullable(),
  status: z.string(),
  timezone: z.string(),
  currencyCode: z.string().length(3),
  countryCode: z.string().length(2).nullable(),
  addressLine1: z.string().nullable(),
  addressLine2: z.string().nullable(),
  city: z.string().nullable(),
  stateRegion: z.string().nullable(),
  postalCode: z.string().nullable(),
  contactEmail: z.string().nullable(),
  contactPhone: z.string().nullable(),
  checkInTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/),
  checkOutTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/),
  taxRateBps: z.number().int(),
  amenities: z.array(z.string()),
  createdAt: z.string().datetime({ offset: true }),
});
export type PropertyResponse = z.infer<typeof PropertyResponseSchema>;

// Mirrors com.altstay.api.property.PropertyController.UpdatePropertyRequest. No `slug` — it's
// read-only after creation and isn't part of this request at all. `PUT` takes the full request,
// so the settings form always submits every field, including ones the user didn't touch
// (load-modify-save, not a patch — phase-6 §4.7). Only `taxRateBps` carries a real backend
// constraint (`@Min(0) @Max(10000)`); the rest is boundary validation this BFF adds on its own,
// since the Java DTO itself has no `@Valid` beyond that one field.
export const UpdatePropertyRequestSchema = z.object({
  name: z.string().min(1, 'name is required'),
  legalName: z.string().nullable(),
  description: z.string().nullable(),
  status: z.string().min(1, 'status is required'),
  timezone: z.string().min(1, 'timezone is required'),
  currencyCode: z.string().length(3),
  countryCode: z.string().length(2).nullable(),
  addressLine1: z.string().nullable(),
  addressLine2: z.string().nullable(),
  city: z.string().nullable(),
  stateRegion: z.string().nullable(),
  postalCode: z.string().nullable(),
  contactEmail: z.string().nullable(),
  contactPhone: z.string().nullable(),
  checkInTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/),
  checkOutTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/),
  taxRateBps: z.number().int().min(0).max(10000),
  amenities: z.array(z.string()),
});
export type UpdatePropertyRequest = z.infer<typeof UpdatePropertyRequestSchema>;
