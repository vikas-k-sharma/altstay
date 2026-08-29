'use client';

import { PresetPicker } from './PresetPicker';
import { RulesEditor } from './RulesEditor';
import { Sliders, Building, Zap, AlertTriangle } from 'lucide-react';

interface AdminPanelProps {
  propertyName: string;
  knowledgeBase: string;
  activePresetId: string;
  onPropertyNameChange: (name: string) => void;
  onKnowledgeBaseChange: (kb: string) => void;
  onSelectPreset: (presetId: string) => void;
}

export function AdminPanel({
  propertyName,
  knowledgeBase,
  activePresetId,
  onPropertyNameChange,
  onKnowledgeBaseChange,
  onSelectPreset,
}: AdminPanelProps) {
  const isOverLimit = knowledgeBase.length > 20000;
  const isEmpty = knowledgeBase.trim().length === 0;

  return (
    <div className="flex flex-col h-full space-y-4 py-2 px-1 max-w-2xl mx-auto w-full">
      {/* Admin Header Banner */}
      <div className="p-4 rounded-2xl bg-linear-to-r from-emerald-500/10 via-teal-500/5 to-transparent border border-emerald-500/20 dark:border-emerald-500/10">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="p-1.5 rounded-lg bg-emerald-500 text-white shadow-xs">
              <Sliders className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-sm font-bold text-zinc-900 dark:text-white">Hostel Admin Console</h2>
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                Live Knowledge Base & Receptionist Guardrails
              </p>
            </div>
          </div>

          <div className="flex items-center gap-1.5 text-xs text-emerald-700 dark:text-emerald-300 font-medium bg-white/80 dark:bg-zinc-800/80 px-2.5 py-1 rounded-full border border-emerald-200 dark:border-emerald-800 shadow-xs">
            <Zap className="w-3.5 h-3.5 text-amber-500 fill-amber-500" />
            <span>Zero-Save Sync</span>
          </div>
        </div>

        <p className="text-xs text-zinc-600 dark:text-zinc-300 mt-2.5 leading-relaxed">
          <strong className="text-zinc-900 dark:text-white">The Demo Loop:</strong> Edit check-in time or a rate in the rules below, then send a message in the WhatsApp phone on the left. The AI receptionist answers with your update immediately.
        </p>
      </div>

      {/* Validation Error Banner */}
      {(isOverLimit || isEmpty) && (
        <div className="p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-700 dark:text-rose-300 text-xs flex items-center gap-2 animate-in fade-in">
          <AlertTriangle className="w-4 h-4 shrink-0 text-rose-500" />
          <span>
            {isOverLimit
              ? 'Knowledge base exceeds the 20,000 character limit. Shorten rules to enable chat.'
              : 'Knowledge base is empty. Add hostel rules to enable chat.'}
          </span>
        </div>
      )}

      {/* Property Name Input */}
      <div className="p-3.5 rounded-2xl bg-white dark:bg-zinc-800/90 border border-zinc-200 dark:border-zinc-700 shadow-xs space-y-1.5">
        <label
          htmlFor="propertyNameInput"
          className="flex items-center gap-1.5 text-xs font-semibold text-zinc-700 dark:text-zinc-300"
        >
          <Building className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
          Hostel Property Name
        </label>
        <input
          id="propertyNameInput"
          type="text"
          value={propertyName}
          onChange={(e) => onPropertyNameChange(e.target.value)}
          placeholder="e.g. Zostel Plus Goa"
          className="w-full px-3 py-2 rounded-xl text-sm bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-700 text-zinc-900 dark:text-zinc-100 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-transparent font-medium"
        />
      </div>

      {/* Preset Selector */}
      <PresetPicker activePresetId={activePresetId} onSelectPreset={onSelectPreset} />

      {/* Live Rules Monospace Editor */}
      <RulesEditor value={knowledgeBase} onChange={onKnowledgeBaseChange} />
    </div>
  );
}
