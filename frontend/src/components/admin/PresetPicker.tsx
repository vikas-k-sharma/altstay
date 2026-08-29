'use client';

import { HOSTEL_PRESETS } from '@/lib/presets';
import { MapPin, Compass } from 'lucide-react';

interface PresetPickerProps {
  activePresetId: string;
  onSelectPreset: (id: string) => void;
}

export function PresetPicker({ activePresetId, onSelectPreset }: PresetPickerProps) {
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between text-xs font-semibold text-zinc-700 dark:text-zinc-300">
        <span className="flex items-center gap-1.5">
          <Compass className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
          Sample Hostel Presets
        </span>
        <span className="text-[11px] text-zinc-400 font-normal">Click to test instantly</span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
        {HOSTEL_PRESETS.map((preset) => {
          const isActive = preset.id === activePresetId;
          return (
            <button
              key={preset.id}
              type="button"
              onClick={() => onSelectPreset(preset.id)}
              className={`p-2.5 rounded-xl border text-left transition-all duration-150 flex flex-col justify-between cursor-pointer ${
                isActive
                  ? 'bg-emerald-50/80 dark:bg-emerald-950/40 border-emerald-500 ring-2 ring-emerald-500/20 text-emerald-950 dark:text-emerald-100 shadow-xs'
                  : 'bg-white dark:bg-zinc-800/80 border-zinc-200 dark:border-zinc-700 text-zinc-700 dark:text-zinc-300 hover:border-zinc-300 dark:hover:border-zinc-600 hover:bg-zinc-50 dark:hover:bg-zinc-800'
              }`}
            >
              <div>
                <div className="font-semibold text-xs leading-tight line-clamp-1">{preset.name}</div>
                <div className="flex items-center gap-1 text-[11px] text-zinc-500 dark:text-zinc-400 mt-1">
                  <MapPin className="w-3 h-3 text-emerald-600 dark:text-emerald-400 shrink-0" />
                  <span className="truncate">{preset.location}</span>
                </div>
              </div>
              {isActive && (
                <div className="mt-2 text-[10px] font-bold text-emerald-600 dark:text-emerald-400 uppercase tracking-wider">
                  ● Active
                </div>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
