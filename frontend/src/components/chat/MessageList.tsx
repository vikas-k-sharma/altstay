'use client';

import { useRef, useEffect } from 'react';
import { UiMessage, ConversationStatus } from '@/hooks/useConversation';
import { MessageBubble } from './MessageBubble';
import { TypingIndicator } from './TypingIndicator';
import { MessageSquareDashed, Sparkles } from 'lucide-react';

interface MessageListProps {
  messages: UiMessage[];
  status: ConversationStatus;
  suggestedQuestions: string[];
  onSelectQuestion: (question: string) => void;
  onRetry: (id: string) => void;
}

export function MessageList({
  messages,
  status,
  suggestedQuestions,
  onSelectQuestion,
  onRetry,
}: MessageListProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const isNearBottomRef = useRef(true);

  // Monitor scroll position to avoid hijacking user scroll when viewing past history
  const handleScroll = () => {
    const el = containerRef.current;
    if (!el) return;
    const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    isNearBottomRef.current = distanceToBottom < 120;
  };

  useEffect(() => {
    const el = containerRef.current;
    if (el && isNearBottomRef.current) {
      el.scrollTo({
        top: el.scrollHeight,
        behavior: 'smooth',
      });
    }
  }, [messages, status]);

  const isEmpty = messages.length === 0;

  return (
    <div
      ref={containerRef}
      onScroll={handleScroll}
      role="log"
      aria-live="polite"
      aria-label="Conversation with concierge"
      className="flex-1 overflow-y-auto p-4 space-y-3 bg-[#efeae2]/50 dark:bg-[#0b141a] transition-colors"
      style={{
        backgroundImage: `radial-gradient(circle at 50% 50%, rgba(0,0,0,0.03) 1px, transparent 1px)`,
        backgroundSize: '24px 24px',
      }}
    >
      {isEmpty ? (
        <div className="h-full flex flex-col items-center justify-center text-center p-4 my-auto animate-in fade-in zoom-in-95 duration-300">
          <div className="w-12 h-12 rounded-2xl bg-emerald-500/10 dark:bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mb-3">
            <MessageSquareDashed className="w-6 h-6" />
          </div>
          <h3 className="font-semibold text-zinc-900 dark:text-zinc-100 text-sm">
            Guest WhatsApp Concierge
          </h3>
          <p className="text-xs text-zinc-500 dark:text-zinc-400 max-w-xs mt-1 mb-4">
            Ask any question about check-in, pricing, policies, or activities. The AI concierge answers directly from the live rules on the right.
          </p>

          {suggestedQuestions.length > 0 && (
            <div className="w-full max-w-xs space-y-2 text-left">
              <div className="flex items-center gap-1.5 text-[11px] font-semibold text-emerald-700 dark:text-emerald-400 uppercase tracking-wider px-1">
                <Sparkles className="w-3 h-3" />
                <span>Suggested Questions</span>
              </div>
              <div className="flex flex-col gap-1.5">
                {suggestedQuestions.map((q, i) => (
                  <button
                    key={i}
                    type="button"
                    onClick={() => onSelectQuestion(q)}
                    className="w-full text-left px-3 py-2 text-xs rounded-xl bg-white dark:bg-zinc-800/90 text-zinc-800 dark:text-zinc-200 border border-zinc-200 dark:border-zinc-700/80 hover:border-emerald-500 dark:hover:border-emerald-500 hover:bg-emerald-50/50 dark:hover:bg-emerald-950/20 transition-all duration-150 shadow-xs active:scale-98 cursor-pointer"
                  >
                    💬 {q}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      ) : (
        <>
          {messages.map((msg) => (
            <MessageBubble key={msg.id} message={msg} onRetry={onRetry} />
          ))}
          {status === 'sending' && <TypingIndicator />}
        </>
      )}
    </div>
  );
}
