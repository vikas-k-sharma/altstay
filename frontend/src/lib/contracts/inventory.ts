import { z } from 'zod';

// Mirrors the nested records in com.altstay.api.inventory.InventoryService.

export const RoomTypeDtoSchema = z.object({
  id: z.string().uuid(),
  tenantId: z.string().uuid(),
  propertyId: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  saleMode: z.enum(['PER_UNIT', 'WHOLE']),
  kind: z.enum(['DORM', 'PRIVATE']),
  maxOccupancy: z.number().int(),
  baseRateMinor: z.number().int(),
  description: z.string().nullable(),
  isActive: z.boolean(),
  spaceIds: z.array(z.string().uuid()),
});
export type RoomTypeDto = z.infer<typeof RoomTypeDtoSchema>;

// The Java DTOs carry no `@Valid` constraints beyond what InventoryService checks itself
// (validateKind/validateSaleMode/validateUnitKind, all IllegalArgumentException → 400) — these
// request schemas add boundary validation the backend doesn't, same reasoning as GuestDto.
export const CreateRoomTypeRequestSchema = z.object({
  code: z.string().min(1, 'code is required'),
  name: z.string().min(1, 'name is required'),
  saleMode: z.enum(['PER_UNIT', 'WHOLE']),
  kind: z.enum(['DORM', 'PRIVATE']),
  maxOccupancy: z.number().int().min(1),
  baseRateMinor: z.number().int().min(0),
  description: z.string().nullable(),
  isActive: z.boolean(),
});
export type CreateRoomTypeRequest = z.infer<typeof CreateRoomTypeRequestSchema>;

export const UpdateRoomTypeRequestSchema = z.object({
  name: z.string().min(1, 'name is required'),
  saleMode: z.enum(['PER_UNIT', 'WHOLE']),
  kind: z.enum(['DORM', 'PRIVATE']),
  maxOccupancy: z.number().int().min(1),
  baseRateMinor: z.number().int().min(0),
  description: z.string().nullable(),
  isActive: z.boolean(),
});
export type UpdateRoomTypeRequest = z.infer<typeof UpdateRoomTypeRequestSchema>;

export const UnitDtoSchema = z.object({
  id: z.string().uuid(),
  tenantId: z.string().uuid(),
  spaceId: z.string().uuid(),
  label: z.string(),
  unitKind: z.enum(['SINGLE', 'BUNK_TOP', 'BUNK_BOTTOM', 'DOUBLE']),
  isActive: z.boolean(),
});
export type UnitDto = z.infer<typeof UnitDtoSchema>;

// `capacity` is derived (the count of active units) and never stored — phase-5 §3.2 — so it is
// never sent on a request, only ever read on the response.
export const SpaceDtoSchema = z.object({
  id: z.string().uuid(),
  tenantId: z.string().uuid(),
  propertyId: z.string().uuid(),
  name: z.string(),
  floor: z.string().nullable(),
  isActive: z.boolean(),
  capacity: z.number().int(),
  units: z.array(UnitDtoSchema),
});
export type SpaceDto = z.infer<typeof SpaceDtoSchema>;

export const CreateUnitRequestSchema = z.object({
  label: z.string().min(1, 'label is required'),
  unitKind: z.enum(['SINGLE', 'BUNK_TOP', 'BUNK_BOTTOM', 'DOUBLE']),
  isActive: z.boolean(),
});
export type CreateUnitRequest = z.infer<typeof CreateUnitRequestSchema>;

export const CreateSpaceRequestSchema = z.object({
  name: z.string().min(1, 'name is required'),
  floor: z.string().nullable(),
  isActive: z.boolean(),
  units: z.array(CreateUnitRequestSchema).min(1, 'A space must have at least one unit'),
});
export type CreateSpaceRequest = z.infer<typeof CreateSpaceRequestSchema>;

// `units: null` is load-bearing, not an oversight — InventoryService.updateSpace only touches
// units when the field is non-null, and when it IS given, it deletes every existing unit row and
// recreates them with new ids (`unitRepository.deleteBySpaceId` then re-insert). Any allocation
// referencing one of the old unit ids is affected. The console never sends a non-null `units`
// from the plain name/floor/status edit — only from a separate, explicitly-labelled "replace
// beds" action (phase-6 §4.8's zero-mapping warning exists for a related reason: inventory
// changes here are not free of consequence).
export const UpdateSpaceRequestSchema = z.object({
  name: z.string().min(1, 'name is required'),
  floor: z.string().nullable(),
  isActive: z.boolean(),
  units: z.array(CreateUnitRequestSchema).min(1, 'A space must have at least one unit').nullable(),
});
export type UpdateSpaceRequest = z.infer<typeof UpdateSpaceRequestSchema>;
