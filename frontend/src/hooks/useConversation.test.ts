import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useConversation } from './useConversation';
import * as api from '@/lib/api';

vi.mock('@/lib/api', () => ({
  sendChatMessage: vi.fn(),
}));

describe('useConversation Hook', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('initializes with empty messages and idle status', () => {
    const { result } = renderHook(() =>
      useConversation({
        propertyName: 'Zostel Goa',
        knowledgeBase: 'Dorm beds are ₹650.',
      })
    );

    expect(result.current.messages).toEqual([]);
    expect(result.current.status).toBe('idle');
  });

  it('optimistically adds user message and appends assistant reply on success', async () => {
    vi.mocked(api.sendChatMessage).mockResolvedValueOnce({
      reply: 'Dorm bed is ₹650 per night.',
      escalated: false,
      model: 'gemini-2.5-flash',
      usage: { promptTokens: 100, completionTokens: 15, totalTokens: 115 },
      latencyMs: 850,
    });

    const { result } = renderHook(() =>
      useConversation({
        propertyName: 'Zostel Goa',
        knowledgeBase: 'Dorm beds are ₹650.',
      })
    );

    await act(async () => {
      result.current.sendMessage('How much is a dorm bed?');
    });

    expect(result.current.messages).toHaveLength(2);
    expect(result.current.messages[0].role).toBe('USER');
    expect(result.current.messages[0].content).toBe('How much is a dorm bed?');
    expect(result.current.messages[0].failed).toBeUndefined();

    expect(result.current.messages[1].role).toBe('ASSISTANT');
    expect(result.current.messages[1].content).toBe('Dorm bed is ₹650 per night.');
    expect(result.current.messages[1].escalated).toBe(false);
    expect(result.current.messages[1].meta?.model).toBe('gemini-2.5-flash');
    expect(result.current.status).toBe('idle');
  });

  it('serializes history stripping UI-only fields on subsequent sends', async () => {
    vi.mocked(api.sendChatMessage)
      .mockResolvedValueOnce({
        reply: 'Check-in is 2 PM.',
        escalated: false,
        model: 'gemini-2.5-flash',
        usage: { promptTokens: 50, completionTokens: 10, totalTokens: 60 },
        latencyMs: 400,
      })
      .mockResolvedValueOnce({
        reply: 'Check-out is 11 AM.',
        escalated: false,
        model: 'gemini-2.5-flash',
        usage: { promptTokens: 70, completionTokens: 10, totalTokens: 80 },
        latencyMs: 420,
      });

    const { result } = renderHook(() =>
      useConversation({
        propertyName: 'Zostel Goa',
        knowledgeBase: 'Rules...',
      })
    );

    await act(async () => {
      result.current.sendMessage('When is check-in?');
    });

    await act(async () => {
      result.current.sendMessage('When is check-out?');
    });

    expect(api.sendChatMessage).toHaveBeenCalledTimes(2);

    const secondCallArg = vi.mocked(api.sendChatMessage).mock.calls[1][0];
    expect(secondCallArg.history).toEqual([
      { role: 'USER', content: 'When is check-in?' },
      { role: 'ASSISTANT', content: 'Check-in is 2 PM.' },
    ]);
    // Ensure UI fields like id, timestamp, meta, escalated are not present on history elements
    expect(secondCallArg.history[0]).not.toHaveProperty('id');
    expect(secondCallArg.history[0]).not.toHaveProperty('timestamp');
    expect(secondCallArg.history[1]).not.toHaveProperty('meta');
    expect(secondCallArg.history[1]).not.toHaveProperty('escalated');
  });

  it('marks user message as failed and preserves content when API call fails', async () => {
    vi.mocked(api.sendChatMessage).mockRejectedValueOnce(
      new Error('The concierge is offline for a moment. Please retry.')
    );

    const { result } = renderHook(() =>
      useConversation({
        propertyName: 'Zostel Goa',
        knowledgeBase: 'Dorm beds are ₹650.',
      })
    );

    await act(async () => {
      result.current.sendMessage('Can I bring my dog?');
    });

    expect(result.current.messages).toHaveLength(1);
    expect(result.current.messages[0].role).toBe('USER');
    expect(result.current.messages[0].content).toBe('Can I bring my dog?');
    expect(result.current.messages[0].failed).toBe(true);
    expect(result.current.messages[0].errorMessage).toBe(
      'The concierge is offline for a moment. Please retry.'
    );
    expect(result.current.status).toBe('error');
  });

  it('allows retrying a failed message', async () => {
    vi.mocked(api.sendChatMessage)
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValueOnce({
        reply: 'Pets are not allowed.',
        escalated: false,
        model: 'gemini-2.5-flash',
        usage: { promptTokens: 90, completionTokens: 10, totalTokens: 100 },
        latencyMs: 700,
      });

    const { result } = renderHook(() =>
      useConversation({
        propertyName: 'Zostel Goa',
        knowledgeBase: 'No pets allowed.',
      })
    );

    await act(async () => {
      result.current.sendMessage('Can I bring a dog?');
    });

    expect(result.current.messages[0].failed).toBe(true);
    const failedMsgId = result.current.messages[0].id;

    await act(async () => {
      result.current.retry(failedMsgId);
    });

    expect(result.current.messages).toHaveLength(2);
    expect(result.current.messages[0].failed).toBe(false);
    expect(result.current.messages[1].content).toBe('Pets are not allowed.');
    expect(result.current.status).toBe('idle');
  });

  it('clears conversation history', async () => {
    vi.mocked(api.sendChatMessage).mockResolvedValueOnce({
      reply: 'Hi!',
      escalated: false,
      model: 'gemini-2.5-flash',
      usage: { promptTokens: 50, completionTokens: 5, totalTokens: 55 },
      latencyMs: 300,
    });

    const { result } = renderHook(() =>
      useConversation({
        propertyName: 'Zostel Goa',
        knowledgeBase: 'Test KB',
      })
    );

    await act(async () => {
      result.current.sendMessage('Hello');
    });

    expect(result.current.messages).toHaveLength(2);

    act(() => {
      result.current.clearConversation();
    });

    expect(result.current.messages).toEqual([]);
    expect(result.current.status).toBe('idle');
  });
});
