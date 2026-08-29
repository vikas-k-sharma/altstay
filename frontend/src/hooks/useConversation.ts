'use client';

import { useState, useCallback, useTransition } from 'react';
import { sendChatMessage } from '@/lib/api';
import { ChatTurn } from '@/lib/contracts';

export type ConversationStatus = 'idle' | 'sending' | 'error';

export interface MessageMeta {
  model: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  latencyMs: number;
}

export interface UiMessage {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  timestamp: Date;
  escalated?: boolean;
  meta?: MessageMeta;
  failed?: boolean;
  errorMessage?: string;
}

interface UseConversationOptions {
  propertyName: string;
  knowledgeBase: string;
}

export function useConversation({ propertyName, knowledgeBase }: UseConversationOptions) {
  const [messages, setMessages] = useState<UiMessage[]>([]);
  const [status, setStatus] = useState<ConversationStatus>('idle');
  const [, startTransition] = useTransition();

  const executeSend = useCallback(
    async (text: string, existingMessages: UiMessage[], userMessageId: string) => {
      setStatus('sending');

      // Strip UI-only fields and failed turns for backend history
      const history: ChatTurn[] = existingMessages
        .filter((m) => !m.failed)
        .map((m) => ({
          role: m.role,
          content: m.content,
        }));

      try {
        const response = await sendChatMessage({
          propertyName,
          knowledgeBase,
          history,
          message: text,
        });

        startTransition(() => {
          const assistantMsg: UiMessage = {
            id: `assistant-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
            role: 'ASSISTANT',
            content: response.reply,
            timestamp: new Date(),
            escalated: response.escalated,
            meta: {
              model: response.model,
              promptTokens: response.usage.promptTokens,
              completionTokens: response.usage.completionTokens,
              totalTokens: response.usage.totalTokens,
              latencyMs: response.latencyMs,
            },
          };

          setMessages((prev) => [...prev, assistantMsg]);
          setStatus('idle');
        });
      } catch (err: unknown) {
        const errorMessage = err instanceof Error ? err.message : 'Something went wrong';

        startTransition(() => {
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === userMessageId
                ? { ...msg, failed: true, errorMessage }
                : msg
            )
          );
          setStatus('error');
        });
      }
    },
    [propertyName, knowledgeBase]
  );

  const sendMessage = useCallback(
    (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || status === 'sending') return;

      const userMsgId = `user-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`;
      const userMsg: UiMessage = {
        id: userMsgId,
        role: 'USER',
        content: trimmed,
        timestamp: new Date(),
      };

      const currentMessages = messages;
      // Optimistic append
      setMessages((prev) => [...prev, userMsg]);

      executeSend(trimmed, currentMessages, userMsgId);
    },
    [status, messages, executeSend]
  );

  const retry = useCallback(
    (failedMessageId: string) => {
      if (status === 'sending') return;

      const failedIndex = messages.findIndex((m) => m.id === failedMessageId);
      if (failedIndex === -1) return;

      const failedMsg = messages[failedIndex];
      const previousMessages = messages.slice(0, failedIndex);

      // Reset failure state on the message
      setMessages((prev) =>
        prev.map((m) =>
          m.id === failedMessageId
            ? { ...m, failed: false, errorMessage: undefined }
            : m
        )
      );

      executeSend(failedMsg.content, previousMessages, failedMessageId);
    },
    [status, messages, executeSend]
  );

  const clearConversation = useCallback(() => {
    setMessages([]);
    setStatus('idle');
  }, []);

  return {
    messages,
    status,
    sendMessage,
    retry,
    clearConversation,
  };
}
