import Link from 'next/link';
import { requirePropertyContext } from '@/lib/server/property';
import { upstream } from '@/lib/server/session';
import { PropertyAvailabilityResponseSchema } from '@/lib/contracts/availability';
import { propertyToday, addDays } from '@/lib/staff/dates';
import { CalendarTable } from '@/components/staff/CalendarTable';

const DAY_OPTIONS = [14, 30, 60] as const;
const DEFAULT_DAYS = 14;

type CalendarSearchParams = { from?: string; days?: string; roomTypeId?: string };

function clampDays(value: string | undefined): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 1) {
    return DEFAULT_DAYS;
  }
  return Math.min(Math.round(parsed), 60);
}

export default async function CalendarPage({
  searchParams,
}: {
  searchParams: Promise<CalendarSearchParams>;
}) {
  const params = await searchParams;
  const { session, selected: property } = await requirePropertyContext('/console/calendar');
  if (!property) {
    // Unreachable in practice — the (app) layout already blocks rendering when the tenant has no
    // property. Fail loudly rather than render a page that assumes data it doesn't have.
    throw new Error('No active property resolved');
  }

  const from = params.from || propertyToday(property.timezone);
  const days = clampDays(params.days);
  const to = addDays(from, days);

  // Always fetched unfiltered — the roomTypeId filter below is applied to this same response
  // rather than sent to the backend, so the same call also supplies the filter dropdown's options.
  const response = await upstream(
    `/api/v1/properties/${property.slug}/availability?from=${from}&to=${to}`,
    { cookieHeader: session.cookieHeader }
  );
  const availability = response.ok
    ? PropertyAvailabilityResponseSchema.parse(await response.json())
    : null;

  const allRoomTypes = availability?.roomTypes ?? [];
  const displayedRoomTypes = params.roomTypeId
    ? allRoomTypes.filter((roomType) => roomType.roomTypeId === params.roomTypeId)
    : allRoomTypes;

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">Calendar</h1>

      <form method="get" className="flex flex-wrap items-end gap-3 text-sm">
        <div>
          <label htmlFor="from" className="block text-xs font-medium text-text-muted">
            From
          </label>
          <input
            id="from"
            name="from"
            type="date"
            defaultValue={from}
            className="rounded-lg border border-border bg-surface px-2 py-1"
          />
        </div>
        <div>
          <label htmlFor="days" className="block text-xs font-medium text-text-muted">
            Days
          </label>
          <select
            id="days"
            name="days"
            defaultValue={String(days)}
            className="rounded-lg border border-border bg-surface px-2 py-1"
          >
            {DAY_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="roomTypeId" className="block text-xs font-medium text-text-muted">
            Room type
          </label>
          <select
            id="roomTypeId"
            name="roomTypeId"
            defaultValue={params.roomTypeId ?? ''}
            className="rounded-lg border border-border bg-surface px-2 py-1"
          >
            <option value="">All</option>
            {allRoomTypes.map((roomType) => (
              <option key={roomType.roomTypeId} value={roomType.roomTypeId}>
                {roomType.code}
              </option>
            ))}
          </select>
        </div>
        <button type="submit" className="rounded-lg bg-accent px-3 py-1.5 text-sm font-semibold text-white">
          Update
        </button>
      </form>

      {allRoomTypes.length === 0 ? (
        <div className="rounded-lg border border-border bg-surface-muted p-4 text-sm">
          <p className="font-semibold">No active room types yet.</p>
          <p className="text-text-muted">
            {/* Lands in slice 6. */}
            <Link href="/console/settings/inventory" className="text-accent hover:underline">
              Set up inventory
            </Link>{' '}
            before there is anything to show here.
          </p>
        </div>
      ) : (
        <>
          <CalendarTable roomTypes={displayedRoomTypes} currency={availability?.currency ?? 'USD'} />

          {/* The differentiator, made visible: a WHOLE room type shares its physical space with
              PER_UNIT beds, so selling one changes what the other can offer for the same night
              (phase-6 §4.3). A legend, not a hover trick — the plan is explicit about that. */}
          <div className="rounded-lg border border-border bg-surface-muted p-3 text-xs text-text-muted">
            <p>
              <strong className="text-text-muted">PER_UNIT</strong> shows beds available / total for
              that room type.
            </p>
            <p>
              <strong className="text-text-muted">WHOLE</strong> shows the space available / total
              for that night, and the number of spaces free across the <em>entire</em> range shown
              above (&ldquo;bookable whole&rdquo;) next to its name — a room free on 3 of 4 nights
              counts as 0 there, because it can&apos;t be sold for the full stay.
            </p>
            <p>
              A whole-space room type shares physical beds with any dorm mapped to the same space —
              selling one dorm bed for a night can make the whole-room product unavailable for that
              same night, and vice versa. That is the product working as designed, not a bug.
            </p>
          </div>
        </>
      )}
    </div>
  );
}
