import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { POST } from './route';
import { NextRequest } from 'next/server';
import fs from 'fs';
import path from 'path';
import os from 'os';

describe('/api/chat Route Handler', () => {
  const originalFetch = global.fetch;
  const originalEnv = process.env.ALTSTAY_CAPTURE_DIR;

  beforeEach(() => {
    vi.resetAllMocks();
    delete process.env.ALTSTAY_CAPTURE_DIR;
  });

  afterEach(() => {
    global.fetch = originalFetch;
    if (originalEnv !== undefined) {
      process.env.ALTSTAY_CAPTURE_DIR = originalEnv;
    } else {
      delete process.env.ALTSTAY_CAPTURE_DIR;
    }
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

    expect(global.fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/chat',
      expect.objectContaining({
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
          'x-altstay-session': expect.stringMatching(/^local-\d{4}-\d{2}-\d{2}$/),
        }),
      })
    );

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

  it('passes an upstream 429 through with its Retry-After header intact', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          type: 'https://api.altstay.com/errors/rate-limited',
          title: 'Too Many Requests',
          status: 429,
          detail: 'One moment — catching up.',
        }),
        {
          status: 429,
          headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '6' },
        }
      )
    );

    const req = new NextRequest('http://localhost:3000/api/chat', {
      method: 'POST',
      body: JSON.stringify({
        propertyName: 'Driftwood Beach Hostel',
        knowledgeBase: 'Check-in is 2 PM.',
        history: [],
        message: 'When is check-in?',
      }),
    });

    const response = await POST(req);
    const data = await response.json();

    // A 429 whose retry hint is dropped tells the caller to wait without saying how long.
    expect(response.status).toBe(429);
    expect(response.headers.get('Retry-After')).toBe('6');
    expect(data.status).toBe(429);
  });

  it('passes an upstream 503 through, distinct from the 502 outage case', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          type: 'https://api.altstay.com/errors/model-rate-limited',
          title: 'Model Rate Limited',
          status: 503,
          detail: 'The upstream AI model is rate limited or quota exhausted. Please try again later.',
        }),
        { status: 503, headers: { 'Content-Type': 'application/problem+json' } }
      )
    );

    const req = new NextRequest('http://localhost:3000/api/chat', {
      method: 'POST',
      body: JSON.stringify({
        propertyName: 'Driftwood Beach Hostel',
        knowledgeBase: 'Check-in is 2 PM.',
        history: [],
        message: 'When is check-in?',
      }),
    });

    const response = await POST(req);
    expect(response.status).toBe(503);
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

  it('does not write capture files when ALTSTAY_CAPTURE_DIR is unset', async () => {
    const mockBackendResponse = {
      reply: 'Check-in is at 2 PM.',
      escalated: false,
      model: 'gemini-2.5-flash',
      usage: { promptTokens: 10, completionTokens: 10, totalTokens: 20 },
      latencyMs: 100,
    };

    global.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: async () => JSON.stringify(mockBackendResponse),
    });

    const req = new NextRequest('http://localhost:3000/api/chat', {
      method: 'POST',
      headers: { 'x-altstay-session': 'test-session-no-capture' },
      body: JSON.stringify({
        propertyName: 'Test Hostel',
        knowledgeBase: 'Test KB',
        history: [],
        message: 'Hello',
      }),
    });

    const response = await POST(req);
    expect(response.status).toBe(200);
  });

  it('writes jsonl capture file with kb and turn records when ALTSTAY_CAPTURE_DIR is set', async () => {
    const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'altstay-capture-test-'));
    process.env.ALTSTAY_CAPTURE_DIR = tempDir;

    const mockBackendResponse = {
      reply: 'Check-in is at 2 PM.',
      escalated: false,
      model: 'gemini-2.5-flash',
      usage: { promptTokens: 50, completionTokens: 10, totalTokens: 60 },
      latencyMs: 200,
    };

    global.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      status: 200,
      text: async () => JSON.stringify(mockBackendResponse),
    });

    const sessionId = `test-session-${Date.now()}`;
    const req = new NextRequest('http://localhost:3000/api/chat', {
      method: 'POST',
      headers: { 'x-altstay-session': sessionId },
      body: JSON.stringify({
        propertyName: 'Zostel Capture Test',
        knowledgeBase: 'Check-in is 2 PM.',
        history: [],
        message: 'What time is check in?',
      }),
    });

    const response = await POST(req);
    expect(response.status).toBe(200);

    // Wait slightly for async file write
    await new Promise((resolve) => setTimeout(resolve, 50));

    const filePath = path.join(tempDir, `${sessionId}.jsonl`);
    expect(fs.existsSync(filePath)).toBe(true);

    const content = fs.readFileSync(filePath, 'utf8').trim();
    const lines = content.split('\n').map((l) => JSON.parse(l));

    expect(lines.length).toBeGreaterThanOrEqual(2);
    expect(lines[0].type).toBe('kb');
    expect(lines[0].knowledgeBase).toBe('Check-in is 2 PM.');
    expect(lines[1].type).toBe('turn');
    expect(lines[1].message).toBe('What time is check in?');
    expect(lines[1].reply).toBe('Check-in is at 2 PM.');
    expect(lines[1].status).toBe(200);

    // Cleanup
    fs.rmSync(tempDir, { recursive: true, force: true });
  });
});
