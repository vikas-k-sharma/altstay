'use client';

import { useState, useRef, useEffect, KeyboardEvent } from 'react';
import { SendHorizonal, Sparkles } from 'lucide-react';

interface ComposerProps {
  onSend: (text: string) => void;
  disabled?: boolean;
  disabledReason?: string;
  placeholder?: string;
}

export function Composer({
  onSend,
  disabled = false,
  disabledReason,
  placeholder = 'Type a message...',
}: ComposerProps) {
  const [input, setInput] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!disabled && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [disabled]);

  // Auto-resize textarea height
  useEffect(() => {
    const textarea = textareaRef.current;
    if (textarea) {
      textarea.style.height = 'auto';
      textarea.style.height = `${Math.min(textarea.scrollHeight, 120)}px`;
    }
  }, [input]);

  const handleSend = () => {
    const trimmed = input.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed);
    setInput('');
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="p-3 bg-[#f0f2f5] dark:bg-zinc-900 border-t border-zinc-200 dark:border-zinc-800">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleSend();
        }}
        className="flex items-end gap-2 max-w-full"
      >
        <div className="relative flex-1 bg-white dark:bg-zinc-800 rounded-2xl border border-zinc-200 dark:border-zinc-700/80 shadow-inner focus-within:ring-2 focus-within:ring-emerald-500 focus-within:border-transparent transition-all">
          <textarea
            ref={textareaRef}
            rows={1}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={disabled}
            placeholder={disabledReason || placeholder}
            aria-label="Chat message"
            aria-busy={disabled}
            className="w-full px-3.5 py-2.5 max-h-[120px] resize-none bg-transparent text-[14px] text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 focus:outline-hidden leading-relaxed block"
          />
        </div>

        <button
          type="submit"
          disabled={disabled || !input.trim()}
          aria-label="Send message"
          title={disabledReason || 'Send message'}
          className="h-10 w-10 shrink-0 rounded-full flex items-center justify-center bg-emerald-600 hover:bg-emerald-700 text-white disabled:bg-zinc-300 dark:disabled:bg-zinc-800 disabled:text-zinc-500 shadow-sm transition-all duration-150 active:scale-95 focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2"
        >
          {disabled ? (
            <Sparkles className="w-4 h-4 animate-spin text-zinc-400" />
          ) : (
            <SendHorizonal className="w-4 h-4 ml-0.5" />
          )}
        </button>
      </form>
      <div className="mt-1.5 flex items-center justify-between text-[11px] text-zinc-400 dark:text-zinc-500 px-1">
        {disabledReason ? (
          <span className="text-rose-500 font-medium">{disabledReason}</span>
        ) : (
          <span>Press <kbd className="font-mono bg-zinc-200 dark:bg-zinc-800 px-1 rounded text-[10px]">Enter ↵</kbd> to send</span>
        )}
        <span><kbd className="font-mono bg-zinc-200 dark:bg-zinc-800 px-1 rounded text-[10px]">Shift+Enter</kbd> newline</span>
      </div>
    </div>
  );
}
