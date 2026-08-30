import 'server-only';

import { cache } from 'react';
import { cookies } from 'next/headers';
import { upstream, PROPERTY_COOKIE_NAME, requireSession, requireRole, type Session } from './session';
import { PropertyResponseSchema, type PropertyResponse } from '@/lib/contracts/property';

export type PropertyContext = {
  properties: PropertyResponse[];
  selected: PropertyResponse | null;
};

/**
 * Deduplicated per request: the (app) layout and each page both need the tenant's property list,
 * and React's `cache()` collapses those into a single upstream call per render pass.
 */
export const listTenantProperties = cache(async (cookieHeader: string): Promise<PropertyResponse[]> => {
  const response = await upstream('/api/v1/properties', { cookieHeader });
  if (!response.ok) {
    return [];
  }
  const body = await response.json().catch(() => undefined);
  const parsed = PropertyResponseSchema.array().safeParse(body);
  return parsed.success ? parsed.data : [];
});

/**
 * Resolves the property switcher's cookie against the tenant's own property list (phase-6 §3.1).
 * A stale or forged slug — or no cookie at all — falls back to the first property; it can never
 * widen access, because the list itself is already scoped by RLS to this tenant.
 */
export async function resolveActiveProperty(cookieHeader: string): Promise<PropertyContext> {
  const properties = await listTenantProperties(cookieHeader);
  if (properties.length === 0) {
    return { properties: [], selected: null };
  }

  const jar = await cookies();
  const requestedSlug = jar.get(PROPERTY_COOKIE_NAME)?.value;
  const selected = properties.find((property) => property.slug === requestedSlug) ?? properties[0];
  return { properties, selected };
}

/**
 * Combines a session with its resolved property, for every page under `(app)` — a couple of
 * lines otherwise repeated on each one. Both calls are `cache()`-backed, so this costs no extra
 * round trip beyond what the layout already paid for the same request.
 *
 * `roles`, when given, gates on role the same way `requireRole` does (redirecting to `/console`)
 * — settings/property (OWNER) and settings/inventory (MANAGER+) both need this; screens open to
 * any authenticated role omit it and get plain `requireSession` underneath.
 */
export async function requirePropertyContext(
  nextPath?: string,
  roles?: readonly string[]
): Promise<{ session: Session } & PropertyContext> {
  const session = roles ? await requireRole(roles, nextPath) : await requireSession(nextPath);
  const context = await resolveActiveProperty(session.cookieHeader);
  return { session, ...context };
}
