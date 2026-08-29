import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { POST } from './route';
import { NextRequest } from 'next/server';

describe('/api/chat Route Handler', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('returns 400 if request body has blank message or missing knowledgeBase', async () => {
    const req = new NextRequest('http://localhost:3000/api/chat', {
      method: 'POST',
      body: JSON.stringify({
        propertyName: 'Zostel',
        knowledgeBase: '',
        message: '',
      }),
    });

    const response = await POST(req);
    const data = await response.json();

    expect(response.status).toBe(400);
    expect(data.title).toBe('Validation Failure');
    expect(data.errors).toBeDefined();
  });

  it('forwards valid payload to backend and returns 200 on success', async () => {
    const mockBackendResponse = {
      reply: 'Check-in is at 2 PM.',
      escalated: false,
      model: 'gemini-2.5-flash',
      usage: { promptTokens: 120, completionTokens: 12, totalTokens: 132 },
      latencyMs: 900,
    };

    global.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: async () => JSON.stringify(mockBackendResponse),
    });

    const req = new NextRequest('http://localhost:3000/api/chat', {
      method: 'POST',
      body: JSON.stringify({
        propertyName: 'Zostel Goa',
        knowledgeBase: 'Check-in is 2 PM.',
        history: [],
        message: 'When is check-in?',
      }),
    });

    const response = await POST(req);
    const data = await response.json();

    expect(response.status).toBe(200);
    expect(data.reply).toBe('Check-in is at 2 PM.');
    expect(data.escalated).toBe(false);
    expect(data.model).toBe('gemini-2.5-flash');
    expect(data.usage.totalTokens).toBe(132);
  });

  it('returns 502 if upstream backend returns 502 or network error', async () => {
    global.fetch = vi.fn().mockRejectedValueOnce(new Error('fetch failed: ECONNREFUSED'));

    const req = new NextRequest('http://localhost:3000/api/chat', {
      method: 'POST',
      body: JSON.stringify({
        propertyName: 'Zostel Goa',
        knowledgeBase: 'Check-in is 2 PM.',
        history: [],
        message: 'When is check-in?',
      }),
    });

    const response = await POST(req);
    const data = await response.json();

    expect(response.status).toBe(502);
    expect(data.title).toBe('Model Unavailable');
  });

  it('returns 504 if upstream call times out', async () => {
    const timeoutError = new Error('The operation was aborted due to timeout');
    timeoutError.name = 'TimeoutError';
    global.fetch = vi.fn().mockRejectedValueOnce(timeoutError);

    const req = new NextRequest('http://localhost:3000/api/chat', {
      method: 'POST',
      body: JSON.stringify({
        propertyName: 'Zostel Goa',
        knowledgeBase: 'Check-in is 2 PM.',
        history: [],
        message: 'When is check-in?',
      }),
    });

    const response = await POST(req);
    const data = await response.json();

    expect(response.status).toBe(504);
    expect(data.title).toBe('Gateway Timeout');
    expect(data.status).toBe(504);
  });
});
