'use client';

import { useState, ReactNode } from 'react';
import { MessageSquare, Sliders, Sparkles } from 'lucide-react';

interface SplitPaneProps {
  chatPanel: ReactNode;
  adminPanel: ReactNode;
  propertyName: string;
}

export function SplitPane({ chatPanel, adminPanel, propertyName }: SplitPaneProps) {
  const [activeTab, setActiveTab] = useState<'chat' | 'admin'>('chat');

  return (
    <div className="min-h-screen flex flex-col bg-zinc-100 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 transition-colors">
      {/* Top Navbar */}
      <header className="sticky top-0 z-30 bg-white/85 dark:bg-zinc-900/85 backdrop-blur-md border-b border-zinc-200 dark:border-zinc-800 px-4 sm:px-6 py-3 transition-colors">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-emerald-600 flex items-center justify-center text-white shadow-md shadow-emerald-500/20 font-bold text-base">
              A
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base font-bold tracking-tight">AltStay</h1>
                <span className="text-[10px] uppercase font-bold tracking-widest px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
                  Phase 2 Demo
                </span>
              </div>
              <p className="text-[11px] text-zinc-500 dark:text-zinc-400 hidden sm:block">
                Zero-Shot WhatsApp Guest Concierge & Live Knowledge Base
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="hidden md:flex items-center gap-2 px-3 py-1 rounded-xl bg-zinc-100 dark:bg-zinc-800 text-xs text-zinc-600 dark:text-zinc-300 border border-zinc-200 dark:border-zinc-700">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span className="font-semibold">{propertyName || 'AltStay Property'}</span>
            </div>

            {/* Mobile Tab Selector (< lg) */}
            <div className="flex lg:hidden bg-zinc-100 dark:bg-zinc-800 p-1 rounded-xl border border-zinc-200 dark:border-zinc-700">
              <button
                type="button"
                onClick={() => setActiveTab('chat')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  activeTab === 'chat'
                    ? 'bg-white dark:bg-zinc-700 text-emerald-700 dark:text-emerald-300 shadow-xs'
                    : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900'
                }`}
              >
                <MessageSquare className="w-3.5 h-3.5" />
                <span>Guest Chat</span>
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('admin')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  activeTab === 'admin'
                    ? 'bg-white dark:bg-zinc-700 text-emerald-700 dark:text-emerald-300 shadow-xs'
                    : 'text-zinc-600 dark:text-zinc-400 hover:text-zinc-900'
                }`}
              >
                <Sliders className="w-3.5 h-3.5" />
                <span>Admin Rules</span>
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Split-Pane Workspace */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-3 sm:p-6">
        {/* Desktop 2-column Grid (lg+) */}
        <div className="hidden lg:grid lg:grid-cols-12 gap-6 items-start">
          {/* Left Pane: WhatsApp Chat Phone (5 cols) */}
          <section
            aria-label="Guest WhatsApp Chat"
            className="lg:col-span-5 flex flex-col items-center sticky top-20"
          >
            {chatPanel}
          </section>

          {/* Right Pane: Live Admin Rules Editor (7 cols) */}
          <section
            aria-label="Hostel Rules and Knowledge Base"
            className="lg:col-span-7 flex flex-col"
          >
            {adminPanel}
          </section>
        </div>

        {/* Mobile View (< lg) */}
        <div className="block lg:hidden">
          {activeTab === 'chat' ? (
            <section aria-label="Guest WhatsApp Chat">{chatPanel}</section>
          ) : (
            <section aria-label="Hostel Rules and Knowledge Base">{adminPanel}</section>
          )}
        </div>
      </main>

      {/* Footer Banner */}
      <footer className="py-3 px-4 text-center text-xs text-zinc-400 dark:text-zinc-500 border-t border-zinc-200 dark:border-zinc-800/80">
        <div className="flex items-center justify-center gap-1.5">
          <Sparkles className="w-3.5 h-3.5 text-emerald-500" />
          <span>AltStay AI Receptionist Engine · Grounded by Spring AI & Google Gemini</span>
        </div>
      </footer>
    </div>
  );
}
