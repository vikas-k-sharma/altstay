'use client';

import { useState, FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { consoleFetch } from '@/lib/staff/clientFetch';
import { formatMinor, parseMajor } from '@/lib/staff/money';
import { addDays } from '@/lib/staff/dates';
import type { RoomTypeDto } from '@/lib/contracts/inventory';
import type { RatePlanDto, RateCalendarDto } from '@/lib/contracts/rate';

const inputClass = 'rounded-lg border border-border bg-surface px-2 py-1 text-sm';
const labelClass = 'block text-xs font-medium text-text-muted';

/**
 * Two calls in, one out (phase-6 §4.9): the parent page supplies the room types, rate plans and
 * the selected plan's calendar (only the dates that have an override); this component owns the
 * two mutations — creating a plan, and setting one amount across a range in a single request,
 * never one request per date.
 */
export function RateCalendarEditor({
  propertySlug,
  currencyCode,
  roomTypes,
  ratePlans,
  selectedRatePlan,
  calendar,
  monthStart,
  monthEnd,
}: {
  propertySlug: string;
  currencyCode: string;
  roomTypes: RoomTypeDto[];
  ratePlans: RatePlanDto[];
  selectedRatePlan: RatePlanDto | null;
  calendar: RateCalendarDto[];
  monthStart: string;
  monthEnd: string;
}) {
  const router = useRouter();

  const [newRoomTypeId, setNewRoomTypeId] = useState('');
  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');
  const [newIsDefault, setNewIsDefault] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const [rangeFrom, setRangeFrom] = useState(monthStart);
  const [rangeTo, setRangeTo] = useState(monthStart);
  const [rangeAmount, setRangeAmount] = useState('0');
  const [rangeError, setRangeError] = useState<string | null>(null);
  const [rangeSaved, setRangeSaved] = useState(false);
  const [rangeSubmitting, setRangeSubmitting] = useState(false);

  const roomType = selectedRatePlan ? roomTypes.find((rt) => rt.id === selectedRatePlan.roomTypeId) : null;
  const overridesByDate = new Map(calendar.map((entry) => [entry.stayDate, entry.amountMinor]));

  const days: string[] = [];
  for (let cursor = monthStart; cursor <= monthEnd; cursor = addDays(cursor, 1)) {
    days.push(cursor);
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setCreating(true);
    setCreateError(null);
    try {
      const response = await consoleFetch(`/api/console/properties/${propertySlug}/rate-plans`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ roomTypeId: newRoomTypeId, code: newCode, name: newName, isDefault: newIsDefault }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setCreateError(body?.detail ?? 'Could not create the rate plan.');
        return;
      }
      setNewRoomTypeId('');
      setNewCode('');
      setNewName('');
      setNewIsDefault(false);
      router.refresh();
    } finally {
      setCreating(false);
    }
  }

  async function handleSetRange(event: FormEvent) {
    event.preventDefault();
    if (!selectedRatePlan) {
      return;
    }
    setRangeSubmitting(true);
    setRangeError(null);
    setRangeSaved(false);
    try {
      let amountMinor: number;
      try {
        amountMinor = parseMajor(rangeAmount, currencyCode);
      } catch {
        setRangeError('Amount must be a number.');
        return;
      }
      const response = await consoleFetch(`/api/console/rate-plans/${selectedRatePlan.id}/calendar`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ from: rangeFrom, to: rangeTo, amountMinor }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        setRangeError(body?.detail ?? 'Could not set the rate.');
        return;
      }
      setRangeSaved(true);
      router.refresh();
    } finally {
      setRangeSubmitting(false);
    }
  }

  return (
    <div className="space-y-6">
      {selectedRatePlan && roomType && (
        <>
          <section>
            <h2 className="font-semibold">
              {roomType.code} · {selectedRatePlan.name}
            </h2>
            <div className="mt-2 grid grid-cols-7 gap-1 text-sm">
              {days.map((day) => {
                const override = overridesByDate.get(day);
                const amountMinor = override ?? roomType.baseRateMinor;
                return (
                  <div key={day} className="rounded-lg border border-border p-2">
                    <div className="text-xs text-text-muted">{day.slice(-2)}</div>
                    <div className={override === undefined ? 'text-text-muted' : ''}>
                      {formatMinor(amountMinor, currencyCode)}
                    </div>
                  </div>
                );
              })}
            </div>
            <p className="mt-1 text-xs text-text-muted">
              Muted amounts fall back to the room type&apos;s base rate — no override is set for
              that date.
            </p>
          </section>

          <form onSubmit={handleSetRange} className="space-y-2 rounded-lg border border-border p-3">
            <h3 className="text-sm font-semibold">Set rate for a range</h3>
            {/* Inclusive on both ends — unlike a booking's checkIn/checkOut, "to" here is included
                (phase-6 §12.1, mirroring RateService.setCalendarRange). */}
            <div className="flex flex-wrap items-end gap-3">
              <div>
                <label className={labelClass} htmlFor="rangeFrom">
                  From
                </label>
                <input
                  id="rangeFrom"
                  type="date"
                  className={inputClass}
                  value={rangeFrom}
                  onChange={(e) => setRangeFrom(e.target.value)}
                />
              </div>
              <div>
                <label className={labelClass} htmlFor="rangeTo">
                  To (inclusive)
                </label>
                <input
                  id="rangeTo"
                  type="date"
                  className={inputClass}
                  value={rangeTo}
                  onChange={(e) => setRangeTo(e.target.value)}
                />
              </div>
              <div>
                <label className={labelClass} htmlFor="rangeAmount">
                  Rate
                </label>
                <input
                  id="rangeAmount"
                  className={`w-24 ${inputClass}`}
                  value={rangeAmount}
                  onChange={(e) => setRangeAmount(e.target.value)}
                />
              </div>
              <button
                type="submit"
                disabled={rangeSubmitting}
                className="rounded-lg bg-accent px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-60"
              >
                {rangeSubmitting ? 'Saving…' : 'Set rate'}
              </button>
            </div>
            {rangeError && (
              <p role="alert" className="text-sm text-danger">
                {rangeError}
              </p>
            )}
            {rangeSaved && !rangeError && <p className="text-sm text-success">Saved.</p>}
          </form>
        </>
      )}

      <form onSubmit={handleCreate} className="space-y-2 rounded-lg border border-border p-3">
        <h3 className="text-sm font-semibold">Create a rate plan</h3>
        <p className="text-xs text-text-muted">A room type with no plan has no price to override.</p>
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className={labelClass} htmlFor="newRoomTypeId">
              Room type
            </label>
            <select
              id="newRoomTypeId"
              className={inputClass}
              value={newRoomTypeId}
              onChange={(e) => setNewRoomTypeId(e.target.value)}
            >
              <option value="">Choose…</option>
              {roomTypes.map((rt) => (
                <option key={rt.id} value={rt.id}>
                  {rt.code}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={labelClass} htmlFor="newCode">
              Code
            </label>
            <input id="newCode" className={inputClass} value={newCode} onChange={(e) => setNewCode(e.target.value)} />
          </div>
          <div>
            <label className={labelClass} htmlFor="newName">
              Name
            </label>
            <input id="newName" className={inputClass} value={newName} onChange={(e) => setNewName(e.target.value)} />
          </div>
          <label className="flex items-center gap-1 text-sm">
            <input type="checkbox" checked={newIsDefault} onChange={(e) => setNewIsDefault(e.target.checked)} />
            Default for this room type
          </label>
          <button
            type="submit"
            disabled={creating || !newRoomTypeId}
            className="rounded-lg bg-accent px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-60"
          >
            {creating ? 'Saving…' : 'Create rate plan'}
          </button>
        </div>
        {createError && (
          <p role="alert" className="text-sm text-danger">
            {createError}
          </p>
        )}
      </form>

      {ratePlans.length > 0 && (
        <section>
          <h3 className="text-sm font-semibold">Existing rate plans</h3>
          <ul className="text-sm text-text-muted">
            {ratePlans.map((plan) => {
              const rt = roomTypes.find((r) => r.id === plan.roomTypeId);
              return (
                <li key={plan.id}>
                  {rt?.code ?? plan.roomTypeId} · {plan.name} {plan.isDefault && '(default)'}
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </div>
  );
}
