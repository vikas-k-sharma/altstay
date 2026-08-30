import { z } from 'zod';

// Mirrors org.springframework.http.ProblemDetail as populated by GlobalExceptionHandler — RFC
// 9457, plus the `errors` map the validation handler adds via setProperty. `instance` is never
// set by any handler, so Spring serializes it as a literal `null` rather than omitting the key.
// Other handlers add their own one-off extension properties (`reference`, `roomTypeId`, …), so
// this schema passes unknown keys through rather than stripping them.
export const ProblemDetailSchema = z
  .object({
    type: z.string(),
    title: z.string(),
    status: z.number().int(),
    detail: z.string(),
    instance: z.string().nullable().optional(),
    errors: z.record(z.string(), z.string()).optional(),
  })
  .passthrough();
export type ProblemDetail = z.infer<typeof ProblemDetailSchema>;
