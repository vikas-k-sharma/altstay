import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';
import authUserFixture from '@/lib/contracts/__fixtures__/auth-user.json';

const cookieStore = new Map<string, { value: string }>();

vi.mock('next/headers', () => ({
  cookies: vi.fn(async () => ({
    get: (name: string) => cookieStore.get(name),
  })),
}));

vi.mock('next/navigation', () => ({
  redirect: vi.fn((path: string) => {
    throw new Error(`NEXT_REDIRECT:${path}`);
  }),
}));

import { listTenantProperties, resolveActiveProperty, requirePropertyContext } from './property';
import { PROPERTY_COOKIE_NAME, SESSION_COOKIE_NAME } from './session';

const secondProperty = {
  ...propertyFixture,
  id: 'd3e4f5a6-7b8c-4d9e-af01-2b3c4d5e6f70',
  slug: 'driftwood-goa-annex',
  name: 'Driftwood Annex',
};

describe('property.ts', () => {
  const originalFetch = global.fetch;
  const originalBackendUrl = process.env.BACKEND_URL;

  beforeEach(() => {
    vi.clearAllMocks();
    cookieStore.clear();
    process.env.BACKEND_URL = 'http://localhost:8080';
  });

  afterEach(() => {
    global.fetch = originalFetch;
    if (originalBackendUrl !== undefined) {
      process.env.BACKEND_URL = originalBackendUrl;
    } else {
      delete process.env.BACKEND_URL;
    }
  });

  describe('listTenantProperties', () => {
    it('returns an empty list when upstream refuses', async () => {
      global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 401 }));
      const properties = await listTenantProperties('JSESSIONID=abc123');
      expect(properties).toEqual([]);
    });

    it('returns an empty list when the body fails to parse', async () => {
      global.fetch = vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify({ nonsense: true }), { status: 200 })
      );
      const properties = await listTenantProperties('JSESSIONID=abc123');
      expect(properties).toEqual([]);
    });

    it('returns the parsed property list on success', async () => {
      global.fetch = vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify([propertyFixture]), { status: 200 })
      );
      const properties = await listTenantProperties('JSESSIONID=abc123');
      expect(properties).toHaveLength(1);
      expect(properties[0].slug).toBe('driftwood-goa');
    });
  });

  describe('resolveActiveProperty', () => {
    it('returns no selection when the tenant has zero properties', async () => {
      global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 }));
      const context = await resolveActiveProperty('JSESSIONID=abc123');
      expect(context).toEqual({ properties: [], selected: null });
    });

    it('falls back to the first property when no cookie is set', async () => {
      global.fetch = vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify([propertyFixture, secondProperty]), { status: 200 })
      );
      const context = await resolveActiveProperty('JSESSIONID=abc123');
      expect(context.selected?.slug).toBe('driftwood-goa');
    });

    it('falls back to the first property when the cookie names a slug outside this tenant', async () => {
      cookieStore.set(PROPERTY_COOKIE_NAME, { value: 'someone-elses-hostel' });
      global.fetch = vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify([propertyFixture, secondProperty]), { status: 200 })
      );
      const context = await resolveActiveProperty('JSESSIONID=abc123');
      expect(context.selected?.slug).toBe('driftwood-goa');
    });

    it('selects the property named by the cookie when it belongs to this tenant', async () => {
      cookieStore.set(PROPERTY_COOKIE_NAME, { value: 'driftwood-goa-annex' });
      global.fetch = vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify([propertyFixture, secondProperty]), { status: 200 })
      );
      const context = await resolveActiveProperty('JSESSIONID=abc123');
      expect(context.selected?.slug).toBe('driftwood-goa-annex');
      expect(context.properties).toHaveLength(2);
    });
  });

  describe('requirePropertyContext', () => {
    it('gates on role when roles are given, redirecting a role-less session to /console', async () => {
      cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
      global.fetch = vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify({ ...authUserFixture, roles: ['FRONT_DESK'] }), { status: 200 })
      ); // /me

      await expect(requirePropertyContext('/console/settings/property', ['OWNER'])).rejects.toThrow(
        'NEXT_REDIRECT:/console'
      );
    });

    it('resolves session and property together when the role matches', async () => {
      cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
      global.fetch = vi
        .fn()
        .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 })) // /me
        .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 })); // /properties

      const result = await requirePropertyContext('/console/settings/property', ['OWNER']);
      expect(result.session.user.tenantSlug).toBe('driftwood');
      expect(result.selected?.slug).toBe('driftwood-goa');
    });
  });
});
