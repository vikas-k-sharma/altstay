'use client';

import { useCallback, useSyncExternalStore } from 'react';
import { DEFAULT_PRESET, HOSTEL_PRESETS, HostelPreset } from '@/lib/presets';

const STORAGE_KB_KEY = 'altstay_knowledge_base';
const STORAGE_NAME_KEY = 'altstay_property_name';
const STORAGE_PRESET_KEY = 'altstay_active_preset_id';

interface KbState {
  propertyName: string;
  knowledgeBase: string;
  activePresetId: string;
}

let memoryState: KbState = {
  propertyName: DEFAULT_PRESET.propertyName,
  knowledgeBase: DEFAULT_PRESET.knowledgeBase,
  activePresetId: DEFAULT_PRESET.id,
};

let isInitialized = false;
const listeners = new Set<() => void>();

function initStore() {
  if (isInitialized || typeof window === 'undefined') return;
  isInitialized = true;
  try {
    const savedName = localStorage.getItem(STORAGE_NAME_KEY);
    const savedKb = localStorage.getItem(STORAGE_KB_KEY);
    const savedPreset = localStorage.getItem(STORAGE_PRESET_KEY);
    if (savedName || savedKb || savedPreset) {
      memoryState = {
        propertyName: savedName ?? DEFAULT_PRESET.propertyName,
        knowledgeBase: savedKb ?? DEFAULT_PRESET.knowledgeBase,
        activePresetId: savedPreset ?? DEFAULT_PRESET.id,
      };
    }
  } catch {
    // storage not available
  }
}

function emitChange() {
  listeners.forEach((listener) => listener());
}

let debounceTimer: NodeJS.Timeout | null = null;
function persistToStorage(state: KbState) {
  if (typeof window === 'undefined') return;
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    try {
      localStorage.setItem(STORAGE_NAME_KEY, state.propertyName);
      localStorage.setItem(STORAGE_KB_KEY, state.knowledgeBase);
      localStorage.setItem(STORAGE_PRESET_KEY, state.activePresetId);
    } catch {
      // ignore storage error
    }
  }, 300);
}

function subscribe(listener: () => void) {
  initStore();
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function getSnapshot(): KbState {
  initStore();
  return memoryState;
}

// Must be a stable reference: React compares snapshots with Object.is, and a fresh
// object on every call reads as "the store changed" on every render. This is also the
// snapshot React uses for the client's hydration render, so it must always be the
// pre-localStorage defaults in order to match the server-rendered HTML.
const SERVER_SNAPSHOT: KbState = Object.freeze({
  propertyName: DEFAULT_PRESET.propertyName,
  knowledgeBase: DEFAULT_PRESET.knowledgeBase,
  activePresetId: DEFAULT_PRESET.id,
});

function getServerSnapshot(): KbState {
  return SERVER_SNAPSHOT;
}

export function useKnowledgeBase() {
  const state = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  const setPropertyName = useCallback((propertyName: string) => {
    memoryState = { ...memoryState, propertyName };
    emitChange();
    persistToStorage(memoryState);
  }, []);

  const setKnowledgeBase = useCallback((knowledgeBase: string) => {
    memoryState = { ...memoryState, knowledgeBase };
    emitChange();
    persistToStorage(memoryState);
  }, []);

  const selectPreset = useCallback((presetId: string) => {
    const preset = HOSTEL_PRESETS.find((p) => p.id === presetId);
    if (!preset) return;
    memoryState = {
      propertyName: preset.propertyName,
      knowledgeBase: preset.knowledgeBase,
      activePresetId: preset.id,
    };
    emitChange();
    persistToStorage(memoryState);
  }, []);

  const activePreset: HostelPreset =
    HOSTEL_PRESETS.find((p) => p.id === state.activePresetId) || DEFAULT_PRESET;

  return {
    propertyName: state.propertyName,
    knowledgeBase: state.knowledgeBase,
    activePresetId: state.activePresetId,
    activePreset,
    setPropertyName,
    setKnowledgeBase,
    selectPreset,
  };
}
