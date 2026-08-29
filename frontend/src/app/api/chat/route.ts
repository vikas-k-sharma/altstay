import { NextRequest, NextResponse } from 'next/server';
import { ChatRequestSchema, ChatResponseSchema } from '@/lib/contracts';
import { captureSessionTurn } from '@/lib/capture';

export async function POST(req: NextRequest) {
  const startTime = Date.now();
  const sessionId = req.headers.get('x-altstay-session') || `local-${new Date().toISOString().slice(0, 10)}`;

  let body: unknown;
  try {
    body = await req.json();
  } catch {
    void captureSessionTurn({
      sessionId,
      knowledgeBase: '',
      historyTurns: 0,
      message: '',
      reply: null,
      latencyMs: Date.now() - startTime,
      status: 400,
      errorTitle: 'Invalid JSON',
    });

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
    const rawKb = typeof body === 'object' && body !== null && 'knowledgeBase' in body ? String((body as Record<string, unknown>).knowledgeBase) : '';
    const rawMsg = typeof body === 'object' && body !== null && 'message' in body ? String((body as Record<string, unknown>).message) : '';

    void captureSessionTurn({
      sessionId,
      knowledgeBase: rawKb,
      historyTurns: 0,
      message: rawMsg,
      reply: null,
      latencyMs: Date.now() - startTime,
      status: 400,
      errorTitle: 'Validation Failure',
    });

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
        'x-altstay-session': sessionId,
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
      const errorTitle = typeof responseData === 'object' && responseData !== null && 'title' in responseData
        ? String((responseData as Record<string, unknown>).title)
        : 'Upstream Error';

      void captureSessionTurn({
        sessionId,
        propertyName: parseResult.data.propertyName,
        knowledgeBase: parseResult.data.knowledgeBase,
        historyTurns: parseResult.data.history.length,
        message: parseResult.data.message,
        reply: null,
        latencyMs: Date.now() - startTime,
        status: upstreamResponse.status,
        errorTitle,
      });

      // Retry-After is forwarded, not dropped: a 429 whose retry hint never reaches the browser
      // tells the caller to wait without telling it how long.
      const passthroughHeaders: Record<string, string> = {};
      const retryAfter = upstreamResponse.headers.get('retry-after');
      if (retryAfter) {
        passthroughHeaders['Retry-After'] = retryAfter;
      }

      return NextResponse.json(responseData, {
        status: upstreamResponse.status,
        headers: passthroughHeaders,
      });
    }

    const validatedResponse = ChatResponseSchema.safeParse(responseData);
    if (!validatedResponse.success) {
      void captureSessionTurn({
        sessionId,
        propertyName: parseResult.data.propertyName,
        knowledgeBase: parseResult.data.knowledgeBase,
        historyTurns: parseResult.data.history.length,
        message: parseResult.data.message,
        reply: null,
        latencyMs: Date.now() - startTime,
        status: 502,
        errorTitle: 'Upstream Contract Mismatch',
      });

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

    void captureSessionTurn({
      sessionId,
      propertyName: parseResult.data.propertyName,
      knowledgeBase: parseResult.data.knowledgeBase,
      historyTurns: parseResult.data.history.length,
      message: parseResult.data.message,
      reply: validatedResponse.data.reply,
      escalated: validatedResponse.data.escalated,
      model: validatedResponse.data.model,
      usage: validatedResponse.data.usage,
      latencyMs: validatedResponse.data.latencyMs,
      status: 200,
    });

    return NextResponse.json(validatedResponse.data, { status: 200 });
  } catch (err: unknown) {
    const isTimeout = err instanceof Error && (err.name === 'TimeoutError' || err.name === 'AbortError');
    const status = isTimeout ? 504 : 502;
    const errorTitle = isTimeout ? 'Gateway Timeout' : 'Model Unavailable';

    void captureSessionTurn({
      sessionId,
      propertyName: parseResult.data.propertyName,
      knowledgeBase: parseResult.data.knowledgeBase,
      historyTurns: parseResult.data.history.length,
      message: parseResult.data.message,
      reply: null,
      latencyMs: Date.now() - startTime,
      status,
      errorTitle,
    });

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
