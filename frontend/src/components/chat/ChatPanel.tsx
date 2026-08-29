'use client';

import { UiMessage, ConversationStatus } from '@/hooks/useConversation';
import { MessageList } from './MessageList';
import { Composer } from './Composer';
import { Building2, Trash2 } from 'lucide-react';

interface ChatPanelProps {
  propertyName: string;
  messages: UiMessage[];
  status: ConversationStatus;
  suggestedQuestions: string[];
  disabledReason?: string;
  onSendMessage: (text: string) => void;
  onSelectQuestion: (question: string) => void;
  onRetry: (id: string) => void;
  onClear: () => void;
}

export function ChatPanel({
  propertyName,
  messages,
  status,
  suggestedQuestions,
  disabledReason,
  onSendMessage,
  onSelectQuestion,
  onRetry,
  onClear,
}: ChatPanelProps) {
  const isInputDisabled = status === 'sending' || Boolean(disabledReason);

  return (
    <div className="w-full flex justify-center py-2 px-1">
      <div className="w-full max-w-[420px] h-[660px] sm:h-[720px] flex flex-col rounded-3xl overflow-hidden shadow-xl border-4 border-zinc-800 dark:border-zinc-700 bg-white dark:bg-zinc-900 transition-all">
        {/* Phone Top Notch / Speaker Mockup */}
        <div className="bg-[#075e54] dark:bg-[#121b22] pt-2 pb-1 px-6 flex justify-between items-center text-[10px] text-emerald-100/70 select-none">
          <span className="font-mono">WhatsApp</span>
          <div className="w-16 h-3 bg-black/25 dark:bg-black/50 rounded-full" />
          <span className="font-mono">5G 100%</span>
        </div>

        {/* WhatsApp Header Bar */}
        <div className="bg-[#075e54] dark:bg-[#1f2c34] text-white px-3.5 py-2.5 flex items-center justify-between shadow-md z-10">
          <div className="flex items-center gap-2.5 overflow-hidden">
            <div className="relative w-9 h-9 rounded-full bg-emerald-700 dark:bg-emerald-800 flex items-center justify-center text-white shrink-0 border border-white/20">
              <Building2 className="w-4 h-4" />
              <div className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-emerald-400 border-2 border-[#075e54] dark:border-[#1f2c34] rounded-full" />
            </div>
            <div className="truncate">
              <h2 className="text-sm font-semibold truncate leading-tight">
                {propertyName || 'AltStay Concierge'}
              </h2>
              <p className="text-[11px] text-emerald-100 dark:text-emerald-300/80 leading-none mt-0.5">
                Online · instant AI reply
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClear}
            disabled={messages.length === 0}
            title="Clear conversation"
            aria-label="Clear conversation history"
            className="p-1.5 rounded-full hover:bg-white/10 text-emerald-100 disabled:opacity-40 disabled:hover:bg-transparent transition-colors"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>

        {/* Messages Body */}
        <MessageList
          messages={messages}
          status={status}
          suggestedQuestions={suggestedQuestions}
          disabled={isInputDisabled}
          onSelectQuestion={onSelectQuestion}
          onRetry={onRetry}
        />

        {/* Composer Input Bar */}
        <Composer
          onSend={onSendMessage}
          disabled={isInputDisabled}
          disabledReason={disabledReason}
          placeholder={`Message ${propertyName || 'Hostel'}...`}
        />
      </div>
    </div>
  );
}
