'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { consoleFetch } from '@/lib/staff/clientFetch';
import { legalTransitionsFrom, statusLabel, type BookingStatusValue } from '@/lib/staff/bookingStatus';

/**
 * Illegal transitions are absent, not disabled with a tooltip (phase-6 §4.5) — the legal set comes
 * from `legalTransitionsFrom`, the one place mirroring `BookingStatus.canTransitionTo`. A 409
 * means the status moved under us since the page loaded; that re-fetches and re-renders the
 * actions rather than just showing an error (phase-6 §10).
 */
export function BookingTransitionActions({
  reference,
  status,
}: {
  reference: string;
  status: BookingStatusValue;
}) {
  const router = useRouter();
  const [pendingTarget, setPendingTarget] = useState<BookingStatusValue | null>(null);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const legal = legalTransitionsFrom(status);
  if (legal.length === 0) {
    return null;
  }

  async function confirm(target: BookingStatusValue) {
    setSubmitting(true);
    setError(null);
    try {
      const response = await consoleFetch(`/api/console/bookings/${reference}/transitions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ to: target, reason: reason.trim() || null }),
      });

      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setError(body?.detail ?? 'Could not update the booking. Please try again.');
        if (response.status === 409) {
          // The status moved under us — the stale action set is worse than a moment's flash.
          router.refresh();
        }
        return;
      }

      setPendingTarget(null);
      setReason('');
      router.refresh();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-2">
        {legal.map((target) => (
          <button
            key={target}
            type="button"
            onClick={() => setPendingTarget(target)}
            disabled={submitting}
            className="rounded-lg border border-border px-3 py-1.5 text-sm hover:bg-surface-muted disabled:opacity-60"
          >
            {statusLabel(target)}
          </button>
        ))}
      </div>

      {pendingTarget && (
        <div className="flex flex-wrap items-center gap-2 rounded-lg border border-border bg-surface-muted p-2">
          <span className="text-sm">
            Confirm: {statusLabel(status)} → {statusLabel(pendingTarget)}
          </span>
          <input
            type="text"
            aria-label="Reason (optional)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Reason (optional)"
            className="rounded-lg border border-border bg-surface px-2 py-1 text-sm"
          />
          <button
            type="button"
            onClick={() => confirm(pendingTarget)}
            disabled={submitting}
            className="rounded-lg bg-accent px-3 py-1 text-sm text-white disabled:opacity-60"
          >
            {submitting ? 'Saving…' : 'Confirm'}
          </button>
          <button type="button" onClick={() => setPendingTarget(null)} className="text-sm text-text-muted underline">
            Cancel
          </button>
        </div>
      )}

      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
