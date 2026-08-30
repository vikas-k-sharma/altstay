'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { consoleFetch } from '@/lib/staff/clientFetch';
import type { RoomTypeDto, SpaceDto } from '@/lib/contracts/inventory';

/**
 * The one screen with no equivalent in the software these owners have used (phase-6 §4.8):
 * `RoomTypeDto.spaceIds[]` is the same relation read from the other side, rendered here per space
 * so mapping — and un-mapping — reads as "what can this physical room be sold as" rather than
 * "which rooms does this product include".
 */
export function HybridMappingEditor({
  spaces,
  roomTypes,
}: {
  spaces: SpaceDto[];
  roomTypes: RoomTypeDto[];
}) {
  const router = useRouter();
  const [pendingAdd, setPendingAdd] = useState<Record<string, string>>({});
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function addMapping(spaceId: string, roomTypeId: string) {
    const key = `${roomTypeId}:${spaceId}`;
    setBusyKey(key);
    setError(null);
    try {
      const response = await consoleFetch(`/api/console/room-types/${roomTypeId}/spaces/${spaceId}`, {
        method: 'POST',
      });
      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setError(body?.detail ?? 'Could not add that mapping.');
        return;
      }
      router.refresh();
    } finally {
      setBusyKey(null);
    }
  }

  async function removeMapping(spaceId: string, roomTypeId: string) {
    const key = `${roomTypeId}:${spaceId}`;
    setBusyKey(key);
    setError(null);
    try {
      const response = await consoleFetch(`/api/console/room-types/${roomTypeId}/spaces/${spaceId}`, {
        method: 'DELETE',
      });
      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setError(body?.detail ?? 'Could not remove that mapping.');
        return;
      }
      router.refresh();
    } finally {
      setBusyKey(null);
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-text-muted">
        Room 101 can be six dorm beds on Tuesday and one private room on Saturday. Add it to both,
        and selling either one hides the other for those dates — that coupling is the product, not
        a bug (see the calendar&apos;s legend).
      </p>

      <div className="space-y-3">
        {spaces.map((space) => {
          const mapped = roomTypes.filter((rt) => rt.spaceIds.includes(space.id));
          const unmapped = roomTypes.filter((rt) => !rt.spaceIds.includes(space.id));
          const pendingRoomTypeId = pendingAdd[space.id] ?? '';

          return (
            <div key={space.id} className="flex flex-wrap items-center gap-2 rounded-lg border border-border p-2 text-sm">
              <span className="w-24 shrink-0 font-semibold">
                {space.name} <span className="font-normal text-text-muted">· {space.capacity} bed(s)</span>
              </span>
              <span className="text-text-muted">Sold as:</span>

              {mapped.length === 0 ? (
                <span className="text-warning">— nothing. This room cannot be sold.</span>
              ) : (
                mapped.map((rt) => (
                  <span
                    key={rt.id}
                    className="inline-flex items-center gap-1 rounded-full border border-accent px-2 py-0.5 text-accent"
                  >
                    {rt.name}
                    <button
                      type="button"
                      disabled={busyKey === `${rt.id}:${space.id}`}
                      onClick={() => removeMapping(space.id, rt.id)}
                      aria-label={`Remove ${rt.name} from ${space.name}`}
                      className="disabled:opacity-60"
                    >
                      ×
                    </button>
                  </span>
                ))
              )}

              {unmapped.length > 0 && (
                <span className="flex items-center gap-1">
                  <select
                    aria-label={`Add a room type to ${space.name}`}
                    value={pendingRoomTypeId}
                    onChange={(e) => setPendingAdd((prev) => ({ ...prev, [space.id]: e.target.value }))}
                    className="rounded-lg border border-border bg-surface px-1 py-0.5 text-xs"
                  >
                    <option value="">+ add</option>
                    {unmapped.map((rt) => (
                      <option key={rt.id} value={rt.id}>
                        {rt.name}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    disabled={!pendingRoomTypeId || busyKey === `${pendingRoomTypeId}:${space.id}`}
                    onClick={() => {
                      addMapping(space.id, pendingRoomTypeId);
                      setPendingAdd((prev) => ({ ...prev, [space.id]: '' }));
                    }}
                    className="text-xs text-accent underline disabled:opacity-60"
                  >
                    add
                  </button>
                </span>
              )}
            </div>
          );
        })}
      </div>

      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
