import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { PropertyResponseSchema } from '@/lib/contracts/property';
import { upstream, SESSION_COOKIE_NAME, PROPERTY_COOKIE_NAME } from '@/lib/server/session';

const SwitchPropertyRequestSchema = z.object({ slug: z.string().min(1) });

/**
 * Sets the property-switcher cookie, but only to a slug that actually belongs to the caller's
 * tenant (phase-6 §3.1) — the console layout falls back to the first property on a stale or
 * forged value regardless, so this check is a UX correctness measure, not the security boundary.
 */
export async function POST(req: NextRequest): Promise<NextResponse> {
  let json: unknown;
  try {
    json = await req.json();
  } catch {
    return NextResponse.json(
      {
        type: 'https://api.altstay.com/errors/invalid-json',
        title: 'Invalid JSON',
        status: 400,
        detail: 'Request body must be valid JSON',
      },
      { status: 400 }
    );
  }

  const parsed = SwitchPropertyRequestSchema.safeParse(json);
  if (!parsed.success) {
    return NextResponse.json(
      {
        type: 'https://api.altstay.com/errors/validation-error',
        title: 'Validation Failure',
        status: 400,
        detail: 'The request payload failed validation',
        errors: { slug: 'slug is required' },
      },
      { status: 400 }
    );
  }

  const sessionValue = req.cookies.get(SESSION_COOKIE_NAME)?.value;
  if (!sessionValue) {
    return NextResponse.json(
      {
        type: 'https://api.altstay.com/errors/unauthorized',
        title: 'Unauthorized',
        status: 401,
        detail: 'Authentication is required for this resource',
      },
      { status: 401 }
    );
  }

  const upstreamResponse = await upstream('/api/v1/properties', {
    cookieHeader: `JSESSIONID=${sessionValue}`,
  });

  if (!upstreamResponse.ok) {
    const res = NextResponse.json(await upstreamResponse.json().catch(() => ({})), {
      status: upstreamResponse.status,
    });
    if (upstreamResponse.status === 401) {
      res.cookies.delete(SESSION_COOKIE_NAME);
    }
    return res;
  }

  const properties = PropertyResponseSchema.array().safeParse(await upstreamResponse.json());
  if (!properties.success || !properties.data.some((p) => p.slug === parsed.data.slug)) {
    return NextResponse.json(
      {
        type: 'https://api.altstay.com/errors/not-found',
        title: 'Not Found',
        status: 404,
        detail: 'No property with that slug belongs to this tenant',
      },
      { status: 404 }
    );
  }

  const res = new NextResponse(null, { status: 204 });
  res.cookies.set(PROPERTY_COOKIE_NAME, parsed.data.slug, {
    httpOnly: true,
    sameSite: 'strict',
    path: '/',
    secure: process.env.NODE_ENV === 'production',
  });
  return res;
}
