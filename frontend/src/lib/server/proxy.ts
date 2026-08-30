import 'server-only';

import { NextRequest, NextResponse } from 'next/server';
import type { ZodType } from 'zod';
import { upstream, SESSION_COOKIE_NAME } from './session';

const BASE_ERROR_URI = 'https://api.altstay.com/errors/';

function invalidJson() {
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

function validationFailure(issues: { path: PropertyKey[]; message: string }[]) {
  const errors: Record<string, string> = {};
  for (const issue of issues) {
    errors[issue.path.map(String).join('.') || 'body'] = issue.message;
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

function upstreamContractMismatch() {
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

async function parseUpstreamBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return undefined;
  }
  try {
    return JSON.parse(text);
  } catch {
    return { detail: text };
  }
}

type ProxyConfig<TReq, TRes> = {
  method: string;
  /** Static upstream path, or derived from the incoming request (e.g. a dynamic route param). */
  path: string | ((req: NextRequest) => string);
  requestSchema?: ZodType<TReq>;
  responseSchema?: ZodType<TRes>;
  /** Overrides the status returned to the browser; defaults to the upstream's own status. */
  status?: number;
};

/**
 * Builds a Next.js Route Handler that validates the incoming body, relays it to Spring with the
 * caller's session attached, and validates the response before returning it — the bidirectional
 * validation phase-6 §6 keeps a backend contract change from becoming a blank screen instead of a
 * loud test failure. A non-2xx upstream response passes through as-is (already a ProblemDetail);
 * a 401 additionally clears the dead BFF session cookie (phase-6 §2.4).
 */
export function proxy<TReq = never, TRes = never>(config: ProxyConfig<TReq, TRes>) {
  return async function handler(req: NextRequest): Promise<NextResponse> {
    let outgoingBody: unknown;

    if (config.requestSchema) {
      let json: unknown;
      try {
        json = await req.json();
      } catch {
        return invalidJson();
      }

      const parsed = config.requestSchema.safeParse(json);
      if (!parsed.success) {
        return validationFailure(parsed.error.issues);
      }
      outgoingBody = parsed.data;
    }

    const sessionValue = req.cookies.get(SESSION_COOKIE_NAME)?.value;
    const cookieHeader = sessionValue ? `JSESSIONID=${sessionValue}` : undefined;
    const path = typeof config.path === 'function' ? config.path(req) : config.path;

    const upstreamResponse = await upstream(path, {
      method: config.method,
      cookieHeader,
      headers: outgoingBody !== undefined ? { 'Content-Type': 'application/json' } : undefined,
      body: outgoingBody !== undefined ? JSON.stringify(outgoingBody) : undefined,
    });

    if (upstreamResponse.status === 401) {
      const body = await parseUpstreamBody(upstreamResponse);
      const res = NextResponse.json(body ?? {}, { status: 401 });
      res.cookies.delete(SESSION_COOKIE_NAME);
      return res;
    }

    if (upstreamResponse.status === 204) {
      return new NextResponse(null, { status: 204 });
    }

    const body = await parseUpstreamBody(upstreamResponse);

    if (!upstreamResponse.ok) {
      return NextResponse.json(body, { status: upstreamResponse.status });
    }

    if (config.responseSchema) {
      const parsed = config.responseSchema.safeParse(body);
      if (!parsed.success) {
        return upstreamContractMismatch();
      }
      return NextResponse.json(parsed.data, { status: config.status ?? upstreamResponse.status });
    }

    const status = config.status ?? upstreamResponse.status;
    // Some upstream success responses carry no body at all (e.g. the hybrid-mapping POST/DELETE,
    // §4.8) — NextResponse.json(undefined) throws, so this mirrors the 204 branch above for any
    // other bodiless response rather than only the one status code that happens to guarantee it.
    return body === undefined ? new NextResponse(null, { status }) : NextResponse.json(body, { status });
  };
}
