import { z } from 'zod';

// Mirrors com.altstay.api.amenity.AmenityController.AmenityResponse. No nullable fields — every
// column backing this record is `not null`.
export const AmenityResponseSchema = z.object({
  code: z.string(),
  label: z.string(),
  category: z.string(),
});
export type AmenityResponse = z.infer<typeof AmenityResponseSchema>;
