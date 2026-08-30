'use client';

import { useState } from 'react';
import { consoleFetch } from '@/lib/staff/clientFetch';
import type { GuestDto } from '@/lib/contracts/booking';

type Mode = 'search' | 'new';

export function GuestStep({
  onConfirm,
  onBack,
}: {
  onConfirm: (guest: GuestDto) => void;
  onBack: () => void;
}) {
  const [mode, setMode] = useState<Mode>('search');

  // Existing-guest search
  const [query, setQuery] = useState('');
  const [matches, setMatches] = useState<GuestDto[]>([]);
  const [selected, setSelected] = useState<GuestDto | null>(null);
  const [searching, setSearching] = useState(false);

  // New-guest form
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');

  const [error, setError] = useState<string | null>(null);

  async function handleSearch(value: string) {
    setQuery(value);
    setSelected(null);
    const needle = value.trim().toLowerCase();
    if (needle.length < 2) {
      setMatches([]);
      return;
    }
    setSearching(true);
    try {
      const response = await consoleFetch('/api/console/guests');
      if (!response.ok) {
        return;
      }
      const guests: GuestDto[] = await response.json();
      setMatches(guests.filter((guest) => guest.fullName.toLowerCase().includes(needle)));
    } finally {
      setSearching(false);
    }
  }

  function handleNext() {
    if (mode === 'search') {
      if (!selected) {
        setError('Pick a guest, or switch to "New guest".');
        return;
      }
      setError(null);
      onConfirm(selected);
      return;
    }

    if (!fullName.trim()) {
      setError('Full name is required.');
      return;
    }
    if (!email.trim() && !phone.trim()) {
      setError('An email or phone number is required.');
      return;
    }
    setError(null);
    onConfirm({
      id: null,
      fullName: fullName.trim(),
      email: email.trim() || null,
      phone: phone.trim() || null,
      countryCode: null,
      dateOfBirth: null,
      notes: null,
    });
  }

  return (
    <div className="max-w-md space-y-4">
      <div className="flex gap-2 text-sm">
        <button
          type="button"
          onClick={() => setMode('search')}
          className={`rounded-lg px-3 py-1 ${mode === 'search' ? 'bg-accent text-white' : 'border border-border'}`}
        >
          Existing guest
        </button>
        <button
          type="button"
          onClick={() => setMode('new')}
          className={`rounded-lg px-3 py-1 ${mode === 'new' ? 'bg-accent text-white' : 'border border-border'}`}
        >
          New guest
        </button>
      </div>

      {mode === 'search' ? (
        <div className="relative">
          <label htmlFor="guestSearch" className="block text-xs font-medium text-text-muted">
            Search guest name
          </label>
          <input
            id="guestSearch"
            type="text"
            value={query}
            onChange={(e) => handleSearch(e.target.value)}
            className="w-full rounded-lg border border-border bg-surface px-2 py-1 text-sm"
          />
          {selected ? (
            <p className="mt-1 text-sm">
              Selected: <strong>{selected.fullName}</strong>
            </p>
          ) : (
            !searching &&
            query.trim().length >= 2 &&
            matches.length > 0 && (
              <ul className="mt-1 rounded-lg border border-border bg-surface shadow-md">
                {matches.map((guest) => (
                  <li key={guest.id}>
                    <button
                      type="button"
                      onClick={() => setSelected(guest)}
                      className="block w-full px-2 py-1 text-left text-sm hover:bg-surface-muted"
                    >
                      {guest.fullName}
                    </button>
                  </li>
                ))}
              </ul>
            )
          )}
        </div>
      ) : (
        <div className="space-y-3">
          <div>
            <label htmlFor="fullName" className="block text-xs font-medium text-text-muted">
              Full name
            </label>
            <input
              id="fullName"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              className="w-full rounded-lg border border-border bg-surface px-2 py-1 text-sm"
            />
          </div>
          <div className="flex gap-3">
            <div>
              <label htmlFor="email" className="block text-xs font-medium text-text-muted">
                Email
              </label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="rounded-lg border border-border bg-surface px-2 py-1 text-sm"
              />
            </div>
            <div>
              <label htmlFor="phone" className="block text-xs font-medium text-text-muted">
                Phone
              </label>
              <input
                id="phone"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className="rounded-lg border border-border bg-surface px-2 py-1 text-sm"
              />
            </div>
          </div>
        </div>
      )}

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
          onClick={handleNext}
          className="rounded-lg bg-accent px-3 py-1.5 text-sm font-semibold text-white"
        >
          Next: review
        </button>
      </div>
    </div>
  );
}
