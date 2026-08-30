import { NextRequest, NextResponse } from 'next/server';
import { LoginRequestSchema, AuthUserResponseSchema } from '@/lib/contracts/auth';
import { upstream, SESSION_COOKIE_NAME, sessionCookieOptions } from '@/lib/server/session';

const BASE_ERROR_URI = 'https://api.altstay.com/errors/';

function extractSessionValue(setCookieHeader: string | null): string | null {
  if (!setCookieHeader) {
    return null;
  }
  const match = /JSESSIONID=([^;]+)/.exec(setCookieHeader);
  return match ? match[1] : null;
}

/**
 * Relays login to Spring and re-issues its session id under our own cookie name (phase-6 §2.1).
 * This is the one request that cannot go through the generic proxy() helper: it is the request
 * that *creates* the session cookie rather than attaching an existing one.
 */
export async function POST(req: NextRequest): Promise<NextResponse> {
  let json: unknown;
  try {
    json = await req.json();
  } catch {
    return NextResponse.json(
      {
        type: `${BASE_ERROR_URI}invalid-json`,
        title: 'Invalid JSON',
        status: 400,
        detail: 'Request body must be valid JSON',
      },
      { status: 400 }
    );
  }

  const parsedRequest = LoginRequestSchema.safeParse(json);
  if (!parsedRequest.success) {
    const errors: Record<string, string> = {};
    for (const issue of parsedRequest.error.issues) {
      errors[issue.path.join('.') || 'body'] = issue.message;
    }
    return NextResponse.json(
      {
        type: `${BASE_ERROR_URI}validation-error`,
        title: 'Validation Failure',
        status: 400,
        detail: 'The request payload failed validation',
        errors,
      },
      { status: 400 }
    );
  }

  const upstreamResponse = await upstream('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(parsedRequest.data),
  });

  const text = await upstreamResponse.text();
  let body: unknown;
  try {
    body = text ? JSON.parse(text) : undefined;
  } catch {
    body = { detail: text };
  }

  if (!upstreamResponse.ok) {
    // The backend's refusal is already the single constant message AuthLoginIT enforces
    // (phase-6 §4.1) — passed through, not re-interpreted.
    return NextResponse.json(body, { status: upstreamResponse.status });
  }

  const parsedUser = AuthUserResponseSchema.safeParse(body);
  const sessionValue = extractSessionValue(
    typeof upstreamResponse.headers.getSetCookie === 'function'
      ? upstreamResponse.headers.getSetCookie().join('; ')
      : upstreamResponse.headers.get('set-cookie')
  );

  if (!parsedUser.success || !sessionValue) {
    return NextResponse.json(
      {
        type: `${BASE_ERROR_URI}upstream-contract-mismatch`,
        title: 'Upstream Contract Mismatch',
        status: 502,
        detail: 'Upstream returned an invalid response structure',
      },
      { status: 502 }
    );
  }

  const res = NextResponse.json(parsedUser.data, { status: 200 });
  res.cookies.set(SESSION_COOKIE_NAME, sessionValue, sessionCookieOptions());
  return res;
}
