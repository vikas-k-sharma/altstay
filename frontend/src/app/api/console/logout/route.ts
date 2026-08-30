import { NextRequest, NextResponse } from 'next/server';
import { upstream, SESSION_COOKIE_NAME } from '@/lib/server/session';

/**
 * Clears both sides of the session (phase-6 §2.4): Spring's first, then the BFF cookie. Clearing
 * only the BFF cookie would leave a live session on Spring; clearing only Spring's would leave
 * the console believing it is logged in.
 */
export async function POST(req: NextRequest): Promise<NextResponse> {
  const sessionValue = req.cookies.get(SESSION_COOKIE_NAME)?.value;

  if (sessionValue) {
    await upstream('/api/v1/auth/logout', {
      method: 'POST',
      cookieHeader: `JSESSIONID=${sessionValue}`,
    });
  }

  const res = new NextResponse(null, { status: 204 });
  res.cookies.delete(SESSION_COOKIE_NAME);
  return res;
}
