import { describe, it, expect } from 'vitest';
import { LoginRequestSchema, AuthUserResponseSchema } from './auth';
import authUserFixture from './__fixtures__/auth-user.json';

describe('auth contracts', () => {
  it('parses a recorded AuthUserResponse', () => {
    const parsed = AuthUserResponseSchema.parse(authUserFixture);
    expect(parsed.tenantSlug).toBe('driftwood');
    expect(parsed.roles).toEqual(['OWNER']);
  });

  // fullName is genuinely nullable — AppUser.full_name carries no `nullable = false`, and the
  // provisioning runner doesn't collect one for the owner it creates. A live login against a
  // real provisioned tenant returned fullName: null and failed this schema before this test
  // existed, surfacing to the user as "Upstream returned an invalid response structure".
  it('parses an AuthUserResponse with a null fullName', () => {
    const parsed = AuthUserResponseSchema.safeParse({ ...authUserFixture, fullName: null });
    expect(parsed.success).toBe(true);
  });

  it('accepts a well-formed LoginRequest', () => {
    const parsed = LoginRequestSchema.safeParse({
      tenantSlug: 'driftwood',
      email: 'owner@driftwood.example',
      password: 'hunter2',
    });
    expect(parsed.success).toBe(true);
  });

  it('rejects a LoginRequest missing the workspace slug', () => {
    const parsed = LoginRequestSchema.safeParse({
      tenantSlug: '',
      email: 'owner@driftwood.example',
      password: 'hunter2',
    });
    expect(parsed.success).toBe(false);
  });
});
