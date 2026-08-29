'use client';

import { useState } from 'react';
import { UiMessage } from '@/hooks/useConversation';
import { AlertCircle, CheckCheck, RotateCcw, UserCheck, Sparkles, ChevronDown, ChevronUp } from 'lucide-react';

interface MessageBubbleProps {
  message: UiMessage;
  onRetry?: (id: string) => void;
}

export function MessageBubble({ message, onRetry }: MessageBubbleProps) {
  const [showMetaDetails, setShowMetaDetails] = useState(false);
  const isUser = message.role === 'USER';

  const timeString = new Intl.DateTimeFormat('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  }).format(message.timestamp);

  return (
    <div
      className={`flex flex-col group transition-all duration-150 ${
        isUser ? 'items-end' : 'items-start'
      }`}
    >
      <div
        className={`relative px-3.5 py-2.5 rounded-2xl max-w-[85%] sm:max-w-[80%] text-[14.5px] leading-relaxed shadow-sm border ${
          isUser
            ? 'bg-[#d9fdd3] dark:bg-[#005c4b] text-zinc-900 dark:text-emerald-50 border-emerald-100 dark:border-emerald-700/50 rounded-tr-xs'
            : 'bg-white dark:bg-zinc-800 text-zinc-900 dark:text-zinc-100 border-zinc-100 dark:border-zinc-700/60 rounded-tl-xs'
        } ${message.failed ? 'border-rose-300 dark:border-rose-800 bg-rose-50/50 dark:bg-rose-950/30' : ''}`}
      >
        {/* Message body */}
        <div className="whitespace-pre-wrap break-words">{message.content}</div>

        {/* Escalation chip */}
        {message.escalated && (
          <div
            role="status"
            className="mt-2 flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-amber-500/10 border border-amber-500/30 text-amber-800 dark:text-amber-300 text-xs font-medium"
          >
            <UserCheck className="w-3.5 h-3.5 text-amber-600 dark:text-amber-400 shrink-0" />
            <span>Handing over to property manager</span>
          </div>
        )}

        {/* Footer with timestamp and checks */}
        <div
          className={`flex items-center justify-end gap-1 mt-1 text-[11px] select-none ${
            isUser
              ? 'text-emerald-800/70 dark:text-emerald-300/70'
              : 'text-zinc-400 dark:text-zinc-500'
          }`}
        >
          <span>{timeString}</span>
          {isUser && (
            <CheckCheck
              className={`w-3.5 h-3.5 ${
                message.failed ? 'text-rose-500' : 'text-emerald-600 dark:text-emerald-300'
              }`}
            />
          )}
        </div>
      </div>

      {/* Meta toggle for assistant messages */}
      {!isUser && message.meta && (
        <div className="mt-1 flex flex-col items-start px-1">
          <button
            type="button"
            onClick={() => setShowMetaDetails((prev) => !prev)}
            aria-expanded={showMetaDetails}
            className="flex items-center gap-1 text-[11px] text-zinc-400 dark:text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-300 transition-colors font-mono"
            title="Inspect AI generation metadata"
          >
            <Sparkles className="w-3 h-3 text-emerald-500" />
            <span>
              {message.meta.model} · {message.meta.totalTokens} tok · {message.meta.latencyMs}ms
            </span>
            {showMetaDetails ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
          </button>

          {showMetaDetails && (
            <div className="mt-1 p-2 rounded-md bg-zinc-100 dark:bg-zinc-800/80 border border-zinc-200 dark:border-zinc-700 text-[10.5px] font-mono text-zinc-600 dark:text-zinc-300 space-y-0.5 animate-in fade-in duration-150">
              <div>Prompt tokens: <span className="font-semibold text-zinc-900 dark:text-white">{message.meta.promptTokens}</span></div>
              <div>Completion tokens: <span className="font-semibold text-zinc-900 dark:text-white">{message.meta.completionTokens}</span></div>
              <div>Total tokens: <span className="font-semibold text-zinc-900 dark:text-white">{message.meta.totalTokens}</span></div>
              <div>Response latency: <span className="font-semibold text-zinc-900 dark:text-white">{message.meta.latencyMs}ms</span></div>
            </div>
          )}
        </div>
      )}

      {/* Error state & Retry affordance */}
      {message.failed && (
        <div className="mt-1 flex items-center gap-2 text-xs text-rose-600 dark:text-rose-400 px-1 animate-in fade-in">
          <AlertCircle className="w-3.5 h-3.5 shrink-0" />
          <span>{message.errorMessage || 'Failed to send'}</span>
          {onRetry && (
            <button
              type="button"
              onClick={() => onRetry(message.id)}
              className="inline-flex items-center gap-1 font-semibold text-emerald-600 dark:text-emerald-400 hover:underline ml-1"
            >
              <RotateCcw className="w-3 h-3" />
              Retry
            </button>
          )}
        </div>
      )}
    </div>
  );
}
