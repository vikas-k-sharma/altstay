'use client';

import { useState, FormEvent } from 'react';
import { nightsBetween } from '@/lib/staff/dates';

const MAX_NIGHTS = 30; // A UX guard only — the backend enforces no cap of its own.

export function DatesStep({
  today,
  initialCheckIn,
  initialCheckOut,
  initialAdults,
  initialChildren,
  onConfirm,
}: {
  today: string;
  initialCheckIn: string;
  initialCheckOut: string;
  initialAdults: number;
  initialChildren: number;
  onConfirm: (checkIn: string, checkOut: string, adults: number, children: number) => void;
}) {
  const [checkIn, setCheckIn] = useState(initialCheckIn);
  const [checkOut, setCheckOut] = useState(initialCheckOut);
  const [adults, setAdults] = useState(initialAdults);
  const [children, setChildren] = useState(initialChildren);
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (checkIn < today) {
      setError("Check-in can't be before today.");
      return;
    }
    if (!(checkOut > checkIn)) {
      setError('Check-out must be after check-in.');
      return;
    }
    const nights = nightsBetween(checkIn, checkOut);
    if (nights > MAX_NIGHTS) {
      setError(`Stays longer than ${MAX_NIGHTS} nights aren't supported by this wizard.`);
      return;
    }

    setError(null);
    onConfirm(checkIn, checkOut, adults, children);
  }

  return (
    <form onSubmit={handleSubmit} className="max-w-md space-y-4" noValidate>
      <div className="flex gap-3">
        <div>
          <label htmlFor="checkIn" className="block text-xs font-medium text-text-muted">
            Check-in
          </label>
          <input
            id="checkIn"
            type="date"
            min={today}
            value={checkIn}
            onChange={(e) => setCheckIn(e.target.value)}
            required
            className="rounded-lg border border-border bg-surface px-2 py-1 text-sm"
          />
        </div>
        <div>
          <label htmlFor="checkOut" className="block text-xs font-medium text-text-muted">
            Check-out
          </label>
          <input
            id="checkOut"
            type="date"
            min={checkIn}
            value={checkOut}
            onChange={(e) => setCheckOut(e.target.value)}
            required
            className="rounded-lg border border-border bg-surface px-2 py-1 text-sm"
          />
        </div>
      </div>
      <div className="flex gap-3">
        <div>
          <label htmlFor="adults" className="block text-xs font-medium text-text-muted">
            Adults
          </label>
          <input
            id="adults"
            type="number"
            min={1}
            value={adults}
            onChange={(e) => setAdults(Math.max(1, Number(e.target.value)))}
            className="w-20 rounded-lg border border-border bg-surface px-2 py-1 text-sm"
          />
        </div>
        <div>
          <label htmlFor="children" className="block text-xs font-medium text-text-muted">
            Children
          </label>
          <input
            id="children"
            type="number"
            min={0}
            value={children}
            onChange={(e) => setChildren(Math.max(0, Number(e.target.value)))}
            className="w-20 rounded-lg border border-border bg-surface px-2 py-1 text-sm"
          />
        </div>
      </div>
      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
      <button type="submit" className="rounded-lg bg-accent px-3 py-1.5 text-sm font-semibold text-white">
        Next: room
      </button>
    </form>
  );
}
