'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import type { PropertyResponse } from '@/lib/contracts/property';
import { consoleFetch } from '@/lib/staff/clientFetch';

export function PropertySwitcher({
  properties,
  selectedSlug,
}: {
  properties: PropertyResponse[];
  selectedSlug: string;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleChange(slug: string) {
    if (slug === selectedSlug) {
      return;
    }
    setPending(true);
    setError(null);

    try {
      const response = await consoleFetch('/api/console/property', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ slug }),
      });
      if (!response.ok) {
        setError('Could not switch property. Please try again.');
        return;
      }
      router.refresh();
    } catch {
      setError('Could not switch property. Please try again.');
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="flex items-center gap-2">
      <select
        aria-label="Switch property"
        value={selectedSlug}
        disabled={pending}
        onChange={(e) => handleChange(e.target.value)}
        className="rounded-lg border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 px-2 py-1 text-sm text-zinc-900 dark:text-zinc-100"
      >
        {properties.map((property) => (
          <option key={property.slug} value={property.slug}>
            {property.name}
          </option>
        ))}
      </select>
      {error && (
        <span role="alert" className="text-xs text-red-600 dark:text-red-400">
          {error}
        </span>
      )}
    </div>
  );
}
