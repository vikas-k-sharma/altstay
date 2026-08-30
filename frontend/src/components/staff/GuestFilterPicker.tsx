'use client';

import { useEffect, useState } from 'react';
import { useRouter, usePathname, useSearchParams } from 'next/navigation';
import { consoleFetch } from '@/lib/staff/clientFetch';
import type { GuestDto } from '@/lib/contracts/booking';

/**
 * Guest-name search is two steps because there is no name filter on the booking list (phase-6
 * §1.3, §4.4): query `/api/v1/guests` (via the BFF's read-through), let the user pick, then the
 * *server* filters bookings by `guestId`. Matching names here is for the picker only — it never
 * substitutes for the real, server-side booking filter.
 */
export function GuestFilterPicker({ selectedGuestName }: { selectedGuestName?: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [query, setQuery] = useState('');
  const [matches, setMatches] = useState<GuestDto[]>([]);
  const [open, setOpen] = useState(false);

  const needle = query.trim().toLowerCase();

  useEffect(() => {
    if (needle.length < 2) {
      return;
    }
    let cancelled = false;
    consoleFetch('/api/console/guests').then(async (response) => {
      if (cancelled || !response.ok) {
        return;
      }
      const guests: GuestDto[] = await response.json();
      setMatches(guests.filter((guest) => guest.fullName.toLowerCase().includes(needle)));
    });
    return () => {
      cancelled = true;
    };
  }, [needle]);

  function navigateWith(guestId: string | null) {
    const params = new URLSearchParams(searchParams.toString());
    if (guestId) {
      params.set('guestId', guestId);
    } else {
      params.delete('guestId');
    }
    router.push(`${pathname}?${params.toString()}`);
  }

  if (selectedGuestName) {
    return (
      <div className="flex items-center gap-2 text-sm">
        <span className="text-text-muted">Guest:</span>
        <span>{selectedGuestName}</span>
        <button type="button" onClick={() => navigateWith(null)} className="text-xs text-accent underline">
          Clear
        </button>
      </div>
    );
  }

  return (
    <div className="relative max-w-xs">
      <label htmlFor="guest-search" className="block text-xs font-medium text-text-muted">
        Find a guest
      </label>
      <input
        id="guest-search"
        type="text"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
        }}
        placeholder="Search guest name…"
        className="w-full rounded-lg border border-border bg-surface px-2 py-1 text-sm"
      />
      {open && needle.length >= 2 && matches.length > 0 && (
        <ul role="listbox" className="absolute z-10 mt-1 w-full rounded-lg border border-border bg-surface shadow-md">
          {matches.map((guest) => (
            <li key={guest.id}>
              <button
                type="button"
                onClick={() => {
                  navigateWith(guest.id);
                  setOpen(false);
                  setQuery('');
                }}
                className="block w-full px-2 py-1 text-left text-sm hover:bg-surface-muted"
              >
                {guest.fullName}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
