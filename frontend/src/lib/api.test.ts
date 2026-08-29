import { describe, it, expect, vi, beforeEach } from 'vitest';
import { sendChatMessage } from './api';

const validResponse = {
  reply: 'Check-in is from 2:00 PM.',
  escalated: false,
  model: 'gemini-2.5-flash',
  usage: { promptTokens: 100, completionTokens: 10, totalTokens: 110 },
  latencyMs: 1200,
};

const request = {
  propertyName: 'Driftwood Beach Hostel',
  knowledgeBase: 'Check-in is from 2:00 PM.',
  history: [],
  message: 'what time is check-in?',
};

function mockOk() {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => validResponse,
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function headersOf(fetchMock: ReturnType<typeof vi.fn>): Record<string, string> {
  return fetchMock.mock.calls[0][1].headers as Record<string, string>;
}

describe('sendChatMessage — session capture header', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    window.sessionStorage.clear();
  });

  // The BFF names the capture file after this header. Without it every session fell back to
  // `local-<date>.jsonl`, so two beta sessions on one day interleaved into a single file.
  it('sends an x-altstay-session header', async () => {
    const fetchMock = mockOk();
    await sendChatMessage(request);

    expect(headersOf(fetchMock)['x-altstay-session']).toBeTruthy();
  });

  it('reuses the same session id across calls in one sitting', async () => {
    const first = mockOk();
    await sendChatMessage(request);
    const firstId = headersOf(first)['x-altstay-session'];

    const second = mockOk();
    await sendChatMessage(request);
    const secondId = headersOf(second)['x-altstay-session'];

    expect(secondId).toBe(firstId);
    expect(window.sessionStorage.getItem('altstay_session_id')).toBe(firstId);
  });

  it('issues a distinct id once storage is cleared, so a second partner gets their own file', async () => {
    const first = mockOk();
    await sendChatMessage(request);
    const firstId = headersOf(first)['x-altstay-session'];

    window.sessionStorage.clear();

    const second = mockOk();
    await sendChatMessage(request);
    expect(headersOf(second)['x-altstay-session']).not.toBe(firstId);
  });

  // Capture grouping is a convenience; a guest being unable to chat is not.
  it('still sends a chat request when sessionStorage throws', async () => {
    const throwing = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage disabled');
    });

    const fetchMock = mockOk();
    const result = await sendChatMessage(request);

    expect(result.reply).toBe(validResponse.reply);
    expect(headersOf(fetchMock)['x-altstay-session']).toMatch(/^local-\d{4}-\d{2}-\d{2}$/);

    throwing.mockRestore();
  });

  it('still sends Content-Type', async () => {
    const fetchMock = mockOk();
    await sendChatMessage(request);

    expect(headersOf(fetchMock)['Content-Type']).toBe('application/json');
  });
});

describe('sendChatMessage — error status copy', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    window.sessionStorage.clear();
  });

  it('renders 429 as "One moment — catching up."', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 429,
      json: async () => ({ status: 429, type: 'https://api.altstay.com/errors/rate-limited' }),
    }));

    await expect(sendChatMessage(request)).rejects.toThrow('One moment \u2014 catching up.');
  });

  it('renders 503 as "The concierge is paused right now."', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      json: async () => ({ status: 503, type: 'https://api.altstay.com/errors/model-rate-limited' }),
    }));

    await expect(sendChatMessage(request)).rejects.toThrow('The concierge is paused right now.');
  });

  it('renders 502 as "The concierge is offline for a moment. Please retry."', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 502,
      json: async () => ({ status: 502, type: 'https://api.altstay.com/errors/model-unavailable' }),
    }));

    await expect(sendChatMessage(request)).rejects.toThrow('The concierge is offline for a moment. Please retry.');
  });

  it('renders 504 as "The request timed out. Please retry."', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 504,
      json: async () => ({ status: 504, type: 'https://api.altstay.com/errors/gateway-timeout' }),
    }));

    await expect(sendChatMessage(request)).rejects.toThrow('The request timed out. Please retry.');
  });
});
