'use client';

export function TypingIndicator() {
  return (
    <div className="flex items-center gap-1.5 px-4 py-3 bg-white dark:bg-zinc-800 rounded-2xl rounded-tl-sm shadow-sm w-fit border border-zinc-100 dark:border-zinc-700/60 my-1 animate-in fade-in duration-200">
      <div className="w-2 h-2 rounded-full bg-emerald-500/70 animate-bounce [animation-delay:-0.3s]" />
      <div className="w-2 h-2 rounded-full bg-emerald-500/70 animate-bounce [animation-delay:-0.15s]" />
      <div className="w-2 h-2 rounded-full bg-emerald-500/70 animate-bounce" />
      <span className="sr-only">Concierge is typing...</span>
    </div>
  );
}
