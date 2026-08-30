import { z } from 'zod';

// Mirrors com.altstay.api.auth.dto.LoginRequest.
export const LoginRequestSchema = z.object({
  tenantSlug: z.string().min(1, 'Workspace is required'),
  email: z.string().min(1, 'Email is required'),
  password: z.string().min(1, 'Password is required'),
});
export type LoginRequest = z.infer<typeof LoginRequestSchema>;

// Mirrors com.altstay.api.auth.dto.AuthUserResponse. `roles` carries bare names (OWNER, not
// ROLE_OWNER) — the ROLE_ prefix is a Spring Security implementation detail that never reaches
// the wire (phase-6 §2.3).
//
// `fullName` is genuinely nullable — `AppUser.full_name` carries no `nullable = false`, and the
// provisioning runner (phase-5 §10) doesn't collect one for the owner it creates. Verified against
// a live login response after a hand-written fixture missed it (the fixture had a name; a real
// provisioned owner doesn't): this schema originally required a string here, and every login for
// a nameless owner failed the BFF's response validation with "Upstream returned an invalid
// response structure" — a contract bug masquerading as a network error.
export const AuthUserResponseSchema = z.object({
  userId: z.string().uuid(),
  tenantId: z.string().uuid(),
  tenantSlug: z.string(),
  email: z.string(),
  fullName: z.string().nullable(),
  roles: z.array(z.string()),
});
export type AuthUserResponse = z.infer<typeof AuthUserResponseSchema>;
