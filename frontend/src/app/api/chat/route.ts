import { NextRequest, NextResponse } from 'next/server';
import { ChatRequestSchema, ChatResponseSchema } from '@/lib/contracts';

export async function POST(req: NextRequest) {
  let body: unknown;
  try {
    body = await req.json();
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

  const parseResult = ChatRequestSchema.safeParse(body);
  if (!parseResult.success) {
    const fieldErrors: Record<string, string> = {};
    for (const issue of parseResult.error.issues) {
      const path = issue.path.join('.') || 'body';
      fieldErrors[path] = issue.message;
    }

    return NextResponse.json(
      {
        type: 'https://api.altstay.com/errors/validation-error',
        title: 'Validation Failure',
        status: 400,
        detail: 'The request payload failed validation',
        instance: '/api/chat',
        errors: fieldErrors,
      },
      { status: 400 }
    );
  }

  const backendUrl = (process.env.BACKEND_URL || 'http://localhost:8080').replace(/\/$/, '');
  const targetUrl = `${backendUrl}/api/v1/chat`;

  try {
    const upstreamResponse = await fetch(targetUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(parseResult.data),
      signal: AbortSignal.timeout(25000),
    });

    const responseText = await upstreamResponse.text();
    let responseData: unknown;
    try {
      responseData = JSON.parse(responseText);
    } catch {
      responseData = { detail: responseText };
    }

    if (!upstreamResponse.ok) {
      return NextResponse.json(responseData, {
        status: upstreamResponse.status,
      });
    }

    const validatedResponse = ChatResponseSchema.safeParse(responseData);
    if (!validatedResponse.success) {
      return NextResponse.json(
        {
          type: 'https://api.altstay.com/errors/upstream-contract-mismatch',
          title: 'Upstream Contract Mismatch',
          status: 502,
          detail: 'Upstream returned an invalid response structure',
        },
        { status: 502 }
      );
    }

    return NextResponse.json(validatedResponse.data, { status: 200 });
  } catch (err: unknown) {
    const isTimeout = err instanceof Error && (err.name === 'TimeoutError' || err.name === 'AbortError');
    if (isTimeout) {
      return NextResponse.json(
        {
          type: 'https://api.altstay.com/errors/gateway-timeout',
          title: 'Gateway Timeout',
          status: 504,
          detail: 'Upstream model call timed out after 25 seconds',
          instance: '/api/chat',
        },
        { status: 504 }
      );
    }

    return NextResponse.json(
      {
        type: 'https://api.altstay.com/errors/model-unavailable',
        title: 'Model Unavailable',
        status: 502,
        detail: 'The concierge backend service is currently unreachable',
        instance: '/api/chat',
      },
      { status: 502 }
    );
  }
}
