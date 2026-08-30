'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { consoleFetch } from '@/lib/staff/clientFetch';
import { legalTransitionsFrom, type BookingStatusValue } from '@/lib/staff/bookingStatus';

type QuickTarget = 'CHECKED_IN' | 'CHECKED_OUT';

const LABELS: Record<QuickTarget, string> = {
  CHECKED_IN: 'Check in',
  CHECKED_OUT: 'Check out',
};

/**
 * The Today screen's one-click version of a transition — no reason prompt, no menu of every
 * legal target, because a shift needs speed (roadmap R3). Absent, not disabled, when the target
 * isn't legal from the current status (e.g. a NO_SHOW arrival has no "Check in" button at all).
 *
 * An early check-in is never refused, only noted (phase-6 §4.2) — `earlyCheckIn` on the
 * transition's own response is the only place that flag is ever true; the front-desk *list* this
 * button lives on always reports it as `false` (`BookingService.summaryOf`), so this is the one
 * chance to surface it.
 */
export function QuickCheckInOutButton({
  reference,
  status,
  target,
}: {
  reference: string;
  status: BookingStatusValue;
  target: QuickTarget;
}) {
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!legalTransitionsFrom(status).includes(target)) {
    return null;
  }

  async function handleClick() {
    setSubmitting(true);
    setError(null);
    setNote(null);
    try {
      const response = await consoleFetch(`/api/console/bookings/${reference}/transitions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ to: target, reason: null }),
      });

      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setError(body?.detail ?? 'Could not update the booking. Please try again.');
        if (response.status === 409) {
          router.refresh();
        }
        return;
      }

      const updated = await response.json();
      if (target === 'CHECKED_IN' && updated?.earlyCheckIn) {
        setNote('Early check-in noted');
        setTimeout(() => router.refresh(), 1500);
      } else {
        router.refresh();
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <button
        type="button"
        onClick={handleClick}
        disabled={submitting}
        className="rounded-lg bg-accent px-2 py-1 text-xs font-semibold text-white disabled:opacity-60"
      >
        {submitting ? 'Saving…' : LABELS[target]}
      </button>
      {note && <span className="text-xs text-warning">{note}</span>}
      {error && (
        <span role="alert" className="text-xs text-danger">
          {error}
        </span>
      )}
    </div>
  );
}
