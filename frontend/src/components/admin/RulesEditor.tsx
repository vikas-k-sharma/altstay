'use client';

import { FileText, Sparkles, AlertTriangle } from 'lucide-react';

interface RulesEditorProps {
  value: string;
  onChange: (value: string) => void;
  maxChars?: number;
}

export function RulesEditor({ value, onChange, maxChars = 20000 }: RulesEditorProps) {
  const charCount = value.length;
  const isNearLimit = charCount > maxChars * 0.9;
  const isOverLimit = charCount > maxChars;

  return (
    <div className="flex flex-col flex-1 min-h-[320px] rounded-2xl bg-white dark:bg-zinc-800/90 border border-zinc-200 dark:border-zinc-700 shadow-xs overflow-hidden">
      {/* Rules Toolbar */}
      <div className="px-4 py-2.5 bg-zinc-50 dark:bg-zinc-800 border-b border-zinc-200 dark:border-zinc-700/80 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <FileText className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
          <span className="text-xs font-semibold text-zinc-900 dark:text-zinc-100">
            Knowledge Base & Rules (Markdown / Plain Text)
          </span>
        </div>

        <div className="flex items-center gap-2 text-xs">
          <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 text-[11px] font-medium border border-emerald-200 dark:border-emerald-800">
            <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
            <span>Live Sync</span>
          </div>

          <span
            className={`font-mono text-[11px] px-1.5 py-0.5 rounded ${
              isOverLimit
                ? 'text-rose-600 dark:text-rose-400 font-bold bg-rose-50 dark:bg-rose-950/40'
                : isNearLimit
                ? 'text-amber-600 dark:text-amber-400 font-semibold bg-amber-50 dark:bg-amber-950/40'
                : 'text-zinc-500 dark:text-zinc-400'
            }`}
          >
            {charCount.toLocaleString()} / {maxChars.toLocaleString()} chars
          </span>
        </div>
      </div>

      {/* Editor Body */}
      <div className="relative flex-1 p-3">
        <textarea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Type or paste your hostel rules, check-in policies, dorm prices, and amenities here in your own words..."
          aria-label="Hostel Knowledge Base and Rules"
          className="w-full h-full min-h-[300px] resize-none bg-transparent font-mono text-[13px] text-zinc-900 dark:text-zinc-100 placeholder:text-zinc-400 leading-relaxed focus:outline-hidden selection:bg-emerald-500/20"
          spellCheck={false}
        />
      </div>

      {/* Footer Info */}
      <div className="px-4 py-2 bg-zinc-50/70 dark:bg-zinc-800/50 border-t border-zinc-200 dark:border-zinc-700/80 flex items-center justify-between text-[11px] text-zinc-500 dark:text-zinc-400">
        <div className="flex items-center gap-1">
          <Sparkles className="w-3 h-3 text-emerald-500" />
          <span>Write rules naturally — AI receptionist understands bullets, headings, or unstructured notes.</span>
        </div>
        {isOverLimit && (
          <div className="flex items-center gap-1 text-rose-600 dark:text-rose-400 font-medium">
            <AlertTriangle className="w-3 h-3" />
            <span>Exceeds max limit</span>
          </div>
        )}
      </div>
    </div>
  );
}
