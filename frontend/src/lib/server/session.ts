import 'server-only';

import { cache } from 'react';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import { AuthUserResponseSchema, type AuthUserResponse } from '@/lib/contracts/auth';

// Distinct from Spring's JSESSIONID so the two are never confused in a devtools panel
// (phase-6 §2.2). Holds Spring's own session id, never a token this BFF mints.
export const SESSION_COOKIE_NAME = 'altstay_session';

// Which property is selected in the console header (phase-6 §3.1). Read and validated against
// the tenant's own property list on every request that needs it — a stale or forged value can
// only fall back to the first property, never widen access, because RLS scopes the list.
export const PROPERTY_COOKIE_NAME = 'altstay_property';

/**
 * The BFF cookie's four load-bearing attributes (phase-6 §2.2), asserted on the wire by
 * login.route.test.ts rather than assumed from a framework default — the same discipline that
 * caught `SameSite` missing on Spring's cookie for a whole phase (CLAUDE.md). No `maxAge`: it is
 * a session cookie, expiring with the browser session like Spring's does.
 */
export function sessionCookieOptions() {
  return {
    httpOnly: true,
    sameSite: 'strict' as const,
    path: '/',
    secure: process.env.NODE_ENV === 'production',
  };
}

function backendBaseUrl(): string {
  const url = process.env.BACKEND_URL;
  if (!url) {
    throw new Error('BACKEND_URL is not set');
  }
  return url.replace(/\/$/, '');
}

export type UpstreamInit = RequestInit & { cookieHeader?: string };

/**
 * The only place BACKEND_URL is read (phase-6 §2.3). Every server component and route handler
 * that talks to Spring goes through this function, so there is one call site to audit for the
 * "browser never calls Spring directly" invariant CSRF being disabled on /api/v1/** rests on.
 */
export async function upstream(path: string, init: UpstreamInit = {}): Promise<Response> {
  const { cookieHeader, headers, ...rest } = init;
  const finalHeaders = new Headers(headers);
  if (cookieHeader) {
    finalHeaders.set('Cookie', cookieHeader);
  }

  return fetch(`${backendBaseUrl()}${path}`, {
    ...rest,
    headers: finalHeaders,
    cache: 'no-store',
  });
}

export type Session = {
  /** The `Cookie: JSESSIONID=…` header value to relay on further calls within this request. */
  cookieHeader: string;
  user: AuthUserResponse;
};

/**
 * Reads the BFF session cookie and resolves it against Spring's own session by calling `/me` —
 * never trusts the cookie's mere presence. Returns null for no cookie, an expired session, or a
 * missing-tenant response; all three mean the same thing to a caller (phase-6 §2.4).
 *
 * `cache()`-wrapped: the (app) layout and the page it wraps both call this, and without it every
 * request would cost two `/me` round trips instead of one.
 */
export const getSession = cache(async (): Promise<Session | null> => {
  const jar = await cookies();
  const value = jar.get(SESSION_COOKIE_NAME)?.value;
  if (!value) {
    return null;
  }

  const cookieHeader = `JSESSIONID=${value}`;
  const response = await upstream('/api/v1/auth/me', { cookieHeader });
  if (!response.ok) {
    return null;
  }

  const body = await response.json().catch(() => undefined);
  const parsed = AuthUserResponseSchema.safeParse(body);
  if (!parsed.success) {
    return null;
  }

  return { cookieHeader, user: parsed.data };
});

/** Redirects to `/console/login`, preserving `nextPath` as `?next=` so login returns here. */
export async function requireSession(nextPath?: string): Promise<Session> {
  const session = await getSession();
  if (!session) {
    redirect(nextPath ? `/console/login?next=${encodeURIComponent(nextPath)}` : '/console/login');
  }
  return session;
}

/** As {@link requireSession}, and additionally redirects to `/console` if the role is missing. */
export async function requireRole(roles: readonly string[], nextPath?: string): Promise<Session> {
  const session = await requireSession(nextPath);
  if (!roles.some((role) => session.user.roles.includes(role))) {
    redirect('/console');
  }
  return session;
}
