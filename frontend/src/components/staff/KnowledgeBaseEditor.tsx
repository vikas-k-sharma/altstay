'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { consoleFetch } from '@/lib/staff/clientFetch';
import type { KnowledgeBaseVersionResponse } from '@/lib/contracts/knowledgeBase';

const CHAR_LIMIT = 20_000;

// Postgres counts characters, not UTF-16 code units — the backend computes charCount with
// codePointCount for exactly this reason (an emoji or any non-BMP character makes String.length
// disagree near the boundary). Array.from splits on code points, matching that.
function codePointLength(value: string): number {
  return Array.from(value).length;
}

export function KnowledgeBaseEditor({
  propertyId,
  current,
  history,
}: {
  propertyId: string;
  current: KnowledgeBaseVersionResponse | null;
  history: KnowledgeBaseVersionResponse[];
}) {
  const router = useRouter();
  const [content, setContent] = useState(current?.content ?? '');
  const [savedVersionNo, setSavedVersionNo] = useState<number | null>(current?.versionNo ?? null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [saving, setSaving] = useState(false);
  const [showHistory, setShowHistory] = useState(false);

  const charCount = codePointLength(content);
  const overLimit = charCount > CHAR_LIMIT;

  async function handleSave() {
    setError(null);
    setMessage(null);
    setConflict(false);

    // contentSha256 makes an unchanged save a no-op server-side too, but comparing here means an
    // unchanged save costs no network call at all, and the UI can say so honestly rather than
    // pretending to have saved (phase-6 §4.10).
    if (current && content === current.content) {
      setMessage('No changes to save.');
      return;
    }
    if (overLimit) {
      setError(`Content must not exceed ${CHAR_LIMIT.toLocaleString()} characters.`);
      return;
    }

    setSaving(true);
    try {
      const response = await consoleFetch(`/api/console/knowledge-base/${propertyId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content }),
      });

      if (response.status === 409) {
        setConflict(true);
        setError('Someone else saved first.');
        return;
      }
      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setError(body?.detail ?? 'Could not save. Please try again.');
        return;
      }

      const saved: KnowledgeBaseVersionResponse = await response.json();
      setSavedVersionNo(saved.versionNo);
      setMessage(`Saved as version ${saved.versionNo}.`);
      router.refresh();
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <label htmlFor="kb-content" className="block text-xs font-medium text-text-muted">
          Knowledge base
        </label>
        <textarea
          id="kb-content"
          rows={16}
          value={content}
          onChange={(e) => {
            setContent(e.target.value);
            setMessage(null);
          }}
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 font-mono text-sm"
        />
        <p className={`mt-1 text-xs ${overLimit ? 'text-danger' : 'text-text-muted'}`}>
          {charCount.toLocaleString()} / {CHAR_LIMIT.toLocaleString()} characters
        </p>
      </div>

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={handleSave}
          disabled={saving || overLimit}
          className="rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          {saving ? 'Saving…' : 'Save'}
        </button>
        {savedVersionNo !== null && (
          <span className="text-xs text-text-muted">Current version: {savedVersionNo}</span>
        )}
      </div>

      {message && !error && <p className="text-sm text-success">{message}</p>}
      {error && (
        <div role="alert" className="space-y-1 text-sm text-danger">
          <p>{error}</p>
          {conflict && (
            <button type="button" onClick={() => router.refresh()} className="underline">
              Reload
            </button>
          )}
        </div>
      )}

      <div>
        <button type="button" onClick={() => setShowHistory((prev) => !prev)} className="text-sm text-accent underline">
          {showHistory ? 'Hide history' : 'Show history'}
        </button>
        {showHistory && (
          <ul className="mt-2 space-y-1 text-sm">
            {history.length === 0 && <li className="text-text-muted">No saved versions yet.</li>}
            {history.map((version) => (
              <li key={version.id} className="flex items-center gap-2">
                <span>
                  v{version.versionNo} · {new Date(version.createdAt).toLocaleString()}
                </span>
                {/* Restoring is a new save, not a rewind (phase-6 §4.10) — this only stages the
                    text into the editor above; nothing is saved until Save is pressed. */}
                <button
                  type="button"
                  onClick={() => {
                    setContent(version.content);
                    setMessage(null);
                    setError(null);
                  }}
                  className="text-accent underline"
                >
                  Copy into editor
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
