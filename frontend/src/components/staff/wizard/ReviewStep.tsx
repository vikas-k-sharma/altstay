'use client';

import { useEffect, useRef, useState } from 'react';
import { formatMinor } from '@/lib/staff/money';
import { formatStayRange, nightsBetween } from '@/lib/staff/dates';
import type { QuoteResponse } from '@/lib/contracts/rate';
import type { GuestDto } from '@/lib/contracts/booking';

export function ReviewStep({
  checkIn,
  checkOut,
  roomTypeCode,
  guest,
  quote,
  error,
  onFetchQuote,
  onConfirm,
  onBack,
}: {
  checkIn: string;
  checkOut: string;
  roomTypeCode: string;
  guest: GuestDto;
  quote: QuoteResponse | null;
  error: string | null;
  onFetchQuote: () => void;
  onConfirm: () => Promise<void>;
  onBack: () => void;
}) {
  const [submitting, setSubmitting] = useState(false);
  const fetchedOnce = useRef(false);

  // Fires once per mount — this step only mounts when the wizard actually enters REVIEW, so
  // going back and forward again naturally re-fetches for whatever changed.
  useEffect(() => {
    if (!fetchedOnce.current) {
      fetchedOnce.current = true;
      onFetchQuote();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleConfirm() {
    setSubmitting(true);
    try {
      await onConfirm();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="max-w-md space-y-4">
      <section>
        <h3 className="font-semibold">Stay</h3>
        <p className="text-sm">
          {roomTypeCode} · {formatStayRange(checkIn, checkOut)} · {nightsBetween(checkIn, checkOut)} night(s)
        </p>
      </section>

      <section>
        <h3 className="font-semibold">Guest</h3>
        <p className="text-sm">{guest.fullName}</p>
        <p className="text-sm text-text-muted">
          {guest.email ?? '—'} · {guest.phone ?? '—'}
        </p>
      </section>

      <section>
        <h3 className="font-semibold">Price</h3>
        {quote ? (
          <div className="text-sm">
            <ul className="text-text-muted">
              {quote.nightlyRates.map((night) => (
                <li key={night.date}>
                  {night.date}: {formatMinor(night.rateMinor, quote.currencyCode)}
                </li>
              ))}
            </ul>
            <p className="mt-2">
              Subtotal {formatMinor(quote.subtotalMinor, quote.currencyCode)} + Tax{' '}
              {formatMinor(quote.taxMinor, quote.currencyCode)} = <strong>Total {formatMinor(quote.totalMinor, quote.currencyCode)}</strong>
            </p>
          </div>
        ) : (
          <p className="text-sm text-text-muted">Fetching the quote…</p>
        )}
      </section>

      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}

      <div className="flex gap-2">
        <button type="button" onClick={onBack} className="text-sm text-text-muted underline">
          Back
        </button>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={!quote || submitting}
          className="rounded-lg bg-accent px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-60"
        >
          {submitting ? 'Booking…' : 'Confirm booking'}
        </button>
      </div>
    </div>
  );
}
