'use client';

import { useMemo, useState } from 'react';
import type { PropertyAvailabilityResponse, RoomTypeAvailabilityDto } from '@/lib/contracts/availability';

/**
 * `WHOLE` uses `bookableWholeSpaces` — the range-wide count — never the per-day
 * `availableSpaces` the calendar renders (phase-6 §4.3): using the per-day number here would
 * offer a room that is free on 3 of 4 nights of the requested stay.
 */
function availabilityFor(roomType: RoomTypeAvailabilityDto): number {
  if (roomType.saleMode === 'WHOLE') {
    return roomType.bookableWholeSpaces;
  }
  return roomType.days.length === 0 ? 0 : Math.min(...roomType.days.map((day) => day.availableUnits));
}

export function RoomStep({
  availability,
  initialRoomTypeId,
  onConfirm,
  onBack,
}: {
  availability: PropertyAvailabilityResponse | null;
  initialRoomTypeId: string | null;
  onConfirm: (roomTypeId: string, unitCount: number) => void;
  onBack: () => void;
}) {
  const roomTypes = availability?.roomTypes ?? [];
  const [roomTypeId, setRoomTypeId] = useState<string | null>(
    initialRoomTypeId && roomTypes.some((rt) => rt.roomTypeId === initialRoomTypeId) ? initialRoomTypeId : null
  );
  const [unitCount, setUnitCount] = useState(1);
  const [error, setError] = useState<string | null>(null);

  const selected = roomTypes.find((rt) => rt.roomTypeId === roomTypeId) ?? null;
  const maxAvailable = useMemo(() => (selected ? availabilityFor(selected) : 0), [selected]);

  if (!availability) {
    return <p className="text-sm text-text-muted">Loading availability…</p>;
  }

  function handleNext() {
    if (!roomTypeId || !selected) {
      setError('Pick a room type.');
      return;
    }
    if (maxAvailable <= 0) {
      setError('Nothing available for these dates. Pick another room type or go back and change dates.');
      return;
    }
    const count = selected.saleMode === 'WHOLE' ? 1 : Math.min(unitCount, maxAvailable);
    setError(null);
    onConfirm(roomTypeId, count);
  }

  return (
    <div className="max-w-md space-y-4">
      <fieldset className="space-y-2">
        <legend className="text-xs font-medium text-text-muted">Room type</legend>
        {roomTypes.map((rt) => {
          const available = availabilityFor(rt);
          const soldOut = available <= 0;
          return (
            <label
              key={rt.roomTypeId}
              className={`flex items-center gap-2 rounded-lg border border-border p-2 text-sm ${soldOut ? 'text-text-muted' : ''}`}
            >
              <input
                type="radio"
                name="roomTypeId"
                value={rt.roomTypeId}
                checked={roomTypeId === rt.roomTypeId}
                disabled={soldOut}
                onChange={() => {
                  setRoomTypeId(rt.roomTypeId);
                  setUnitCount(1);
                }}
              />
              <span>
                {rt.code} ({rt.saleMode === 'WHOLE' ? 'whole room' : 'per bed'}) —{' '}
                {soldOut ? 'sold out' : `${available} available`}
              </span>
            </label>
          );
        })}
      </fieldset>

      {selected && selected.saleMode === 'PER_UNIT' && (
        <div>
          <label htmlFor="unitCount" className="block text-xs font-medium text-text-muted">
            Beds
          </label>
          <input
            id="unitCount"
            type="number"
            min={1}
            max={maxAvailable}
            value={unitCount}
            onChange={(e) => setUnitCount(Math.max(1, Number(e.target.value)))}
            className="w-20 rounded-lg border border-border bg-surface px-2 py-1 text-sm"
          />
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
          Next: guest
        </button>
      </div>
    </div>
  );
}
