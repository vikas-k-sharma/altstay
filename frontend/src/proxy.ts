import { NextResponse, type NextRequest } from 'next/server';

// Mirrors SESSION_COOKIE_NAME in src/lib/server/session.ts. Duplicated rather than imported so
// this file never pulls in anything guarded by `import 'server-only'` — Proxy is its own bundle
// and that's untested territory not worth the risk for a one-line constant.
const SESSION_COOKIE_NAME = 'altstay_session';

/**
 * An optimistic, cookie-presence-only gate on `/console/**` (phase-6 §2, §9). This is not the
 * security boundary — it exists only so an unauthenticated visit to a deep console URL redirects
 * to login with the exact `next=` path, which a Server Component layout cannot read for itself.
 * The real check is `requireSession()` in the (app) layout, which validates the cookie's value
 * against Spring's own `/me` rather than trusting its mere presence.
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  if (pathname === '/console/login' || !pathname.startsWith('/console')) {
    return NextResponse.next();
  }

  if (request.cookies.has(SESSION_COOKIE_NAME)) {
    return NextResponse.next();
  }

  const loginUrl = new URL('/console/login', request.url);
  loginUrl.searchParams.set('next', pathname + search);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ['/console/:path*'],
};
