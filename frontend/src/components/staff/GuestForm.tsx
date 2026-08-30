'use client';

import { useState, FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { consoleFetch } from '@/lib/staff/clientFetch';
import type { GuestDto } from '@/lib/contracts/booking';

/** Same form for create and edit — load-modify-save either way, mirroring settings/property. */
export function GuestForm({ guest }: { guest?: GuestDto }) {
  const router = useRouter();
  const [fullName, setFullName] = useState(guest?.fullName ?? '');
  const [email, setEmail] = useState(guest?.email ?? '');
  const [phone, setPhone] = useState(guest?.phone ?? '');
  const [countryCode, setCountryCode] = useState(guest?.countryCode ?? '');
  const [notes, setNotes] = useState(guest?.notes ?? '');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      const url = guest ? `/api/console/guests/${guest.id}` : '/api/console/guests';
      const method = guest ? 'PUT' : 'POST';
      const response = await consoleFetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: guest?.id ?? null,
          fullName,
          email: email.trim() || null,
          phone: phone.trim() || null,
          countryCode: countryCode.trim() || null,
          dateOfBirth: guest?.dateOfBirth ?? null,
          notes: notes.trim() || null,
        }),
      });

      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setError(body?.detail ?? 'Could not save this guest. Please try again.');
        return;
      }

      if (!guest) {
        setFullName('');
        setEmail('');
        setPhone('');
        setCountryCode('');
        setNotes('');
      }
      router.refresh();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-3 text-sm" noValidate>
      <div>
        <label htmlFor="fullName" className="block text-xs font-medium text-text-muted">
          Full name
        </label>
        <input
          id="fullName"
          required
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          className="rounded-lg border border-border bg-surface px-2 py-1"
        />
      </div>
      <div>
        <label htmlFor="email" className="block text-xs font-medium text-text-muted">
          Email
        </label>
        <input
          id="email"
          type="email"
          value={email ?? ''}
          onChange={(e) => setEmail(e.target.value)}
          className="rounded-lg border border-border bg-surface px-2 py-1"
        />
      </div>
      <div>
        <label htmlFor="phone" className="block text-xs font-medium text-text-muted">
          Phone
        </label>
        <input
          id="phone"
          value={phone ?? ''}
          onChange={(e) => setPhone(e.target.value)}
          className="rounded-lg border border-border bg-surface px-2 py-1"
        />
      </div>
      <div>
        <label htmlFor="countryCode" className="block text-xs font-medium text-text-muted">
          Country
        </label>
        <input
          id="countryCode"
          maxLength={2}
          value={countryCode ?? ''}
          onChange={(e) => setCountryCode(e.target.value.toUpperCase())}
          className="w-14 rounded-lg border border-border bg-surface px-2 py-1 uppercase"
        />
      </div>
      <div>
        <label htmlFor="notes" className="block text-xs font-medium text-text-muted">
          Notes
        </label>
        <input
          id="notes"
          value={notes ?? ''}
          onChange={(e) => setNotes(e.target.value)}
          className="rounded-lg border border-border bg-surface px-2 py-1"
        />
      </div>
      <button
        type="submit"
        disabled={submitting}
        className="rounded-lg bg-accent px-3 py-1.5 font-semibold text-white disabled:opacity-60"
      >
        {submitting ? 'Saving…' : guest ? 'Save' : 'Add guest'}
      </button>
      {error && (
        <p role="alert" className="w-full text-sm text-danger">
          {error}
        </p>
      )}
    </form>
  );
}
