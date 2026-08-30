import { describe, it, expect } from 'vitest';
import { ProblemDetailSchema } from './problem';
import unauthorized from './__fixtures__/problem-unauthorized.json';
import validationError from './__fixtures__/problem-validation-error.json';

describe('ProblemDetailSchema', () => {
  it('parses the unauthorized shape, including a null instance', () => {
    const parsed = ProblemDetailSchema.parse(unauthorized);
    expect(parsed.title).toBe('Unauthorized');
    expect(parsed.instance).toBeNull();
    expect(parsed.errors).toBeUndefined();
  });

  it('parses the validation-error shape, including its errors map', () => {
    const parsed = ProblemDetailSchema.parse(validationError);
    expect(parsed.errors).toEqual({ tenantSlug: 'tenantSlug is required' });
  });
});
