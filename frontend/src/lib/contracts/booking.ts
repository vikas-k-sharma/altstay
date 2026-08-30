import { z } from 'zod';

// Mirrors the nested records in com.altstay.api.booking.BookingService. Only what's consumed so
// far — ModifyBookingRequest has no screen that calls for it (phase-6 §12.1).

// GuestDto is also used as a request body (create/update), where `id` is not yet known — Jackson
// still accepts the field present-and-null on the way in. `fullName.min(1)` is a BFF-side
// safeguard, not a mirror of a Java constraint — GuestController takes no `@Valid` on this DTO at
// all, so an empty name would otherwise reach Postgres's `not null` column as an empty string
// rather than a validation error a form can show.
export const GuestDtoSchema = z.object({
  id: z.string().uuid().nullable(),
  fullName: z.string().min(1, 'fullName is required'),
  email: z.string().nullable(),
  phone: z.string().nullable(),
  countryCode: z.string().length(2).nullable(),
  dateOfBirth: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable(),
  notes: z.string().nullable(),
});
export type GuestDto = z.infer<typeof GuestDtoSchema>;

// The wizard always sends `checkIn`/`checkOut` as null on the line (letting the backend default
// to the booking's own dates — `priceLines`) and `amountMinor` as null (letting the backend price
// it, the same way it priced the quote). Neither is a mirror requirement, just how this console
// uses the request shape.
export const CreateBookingLineRequestSchema = z.object({
  roomTypeId: z.string().uuid(),
  spaceId: z.string().uuid().nullable(),
  checkIn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable(),
  checkOut: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable(),
  unitCount: z.number().int().min(1),
  amountMinor: z.number().int().nullable(),
});
export type CreateBookingLineRequest = z.infer<typeof CreateBookingLineRequestSchema>;

export const CreateBookingRequestSchema = z.object({
  propertyId: z.string().uuid().nullable(),
  propertySlug: z.string().nullable(),
  guest: GuestDtoSchema,
  checkIn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  checkOut: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  adults: z.number().int().nullable(),
  children: z.number().int().nullable(),
  source: z.string().nullable(),
  lines: z.array(CreateBookingLineRequestSchema).min(1),
  idempotencyKey: z.string().nullable(),
  notes: z.string().nullable(),
});
export type CreateBookingRequest = z.infer<typeof CreateBookingRequestSchema>;

export const BookingLineResponseSchema = z.object({
  id: z.string().uuid(),
  roomTypeId: z.string().uuid(),
  roomTypeCode: z.string(),
  spaceId: z.string().uuid().nullable(),
  checkIn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  checkOut: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  unitCount: z.number().int(),
  amountMinor: z.number().int(),
});
export type BookingLineResponse = z.infer<typeof BookingLineResponseSchema>;

export const AllocationResponseSchema = z.object({
  id: z.string().uuid(),
  unitId: z.string().uuid(),
  unitLabel: z.string(),
  bookingLineId: z.string().uuid(),
  checkIn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  checkOut: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  releasedAt: z.string().datetime({ offset: true }).nullable(),
});
export type AllocationResponse = z.infer<typeof AllocationResponseSchema>;

export const BookingStatusHistoryResponseSchema = z.object({
  id: z.string().uuid(),
  fromStatus: z.string().nullable(),
  toStatus: z.string(),
  changedBy: z.string().uuid().nullable(),
  reason: z.string().nullable(),
  changedAt: z.string().datetime({ offset: true }),
});
export type BookingStatusHistoryResponse = z.infer<typeof BookingStatusHistoryResponseSchema>;

export const BookingResponseSchema = z.object({
  id: z.string().uuid(),
  reference: z.string(),
  propertyId: z.string().uuid(),
  guestId: z.string().uuid(),
  guest: GuestDtoSchema,
  status: z.enum(['BOOKED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW']),
  source: z.string(),
  checkIn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  checkOut: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  adults: z.number().int(),
  children: z.number().int(),
  currencyCode: z.string().length(3),
  subtotalMinor: z.number().int(),
  taxMinor: z.number().int(),
  totalMinor: z.number().int(),
  amountPaidMinor: z.number().int(),
  paymentState: z.string(),
  idempotencyKey: z.string().nullable(),
  notes: z.string().nullable(),
  lines: z.array(BookingLineResponseSchema),
  allocations: z.array(AllocationResponseSchema),
  statusHistory: z.array(BookingStatusHistoryResponseSchema),
  earlyCheckIn: z.boolean(),
});
export type BookingResponse = z.infer<typeof BookingResponseSchema>;

export const TransitionRequestSchema = z.object({
  to: z.enum(['BOOKED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW']),
  reason: z.string().nullable(),
});
export type TransitionRequest = z.infer<typeof TransitionRequestSchema>;

// One property-local day at the front desk (phase-5 §9). Every BookingResponse here comes from
// BookingService.summaryOf, which passes empty lists for `allocations` and `statusHistory` and a
// hardcoded `false` for `earlyCheckIn` — this is a summary, not the full booking. Room-type
// information for a row therefore comes from `lines[]`, never from `allocations[]`, which is
// always empty on this response despite what an earlier draft of this plan assumed.
export const FrontDeskResponseSchema = z.object({
  propertyId: z.string().uuid(),
  propertySlug: z.string(),
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  arrivals: z.array(BookingResponseSchema),
  departures: z.array(BookingResponseSchema),
  inHouse: z.array(BookingResponseSchema),
});
export type FrontDeskResponse = z.infer<typeof FrontDeskResponseSchema>;
