import Link from 'next/link';
import { requirePropertyContext } from '@/lib/server/property';
import { upstream } from '@/lib/server/session';
import { BookingResponseSchema, GuestDtoSchema, type BookingResponse } from '@/lib/contracts/booking';
import { formatMinor } from '@/lib/staff/money';
import { formatStayRange, nightsBetween } from '@/lib/staff/dates';
import { StatusChip } from '@/components/staff/StatusChip';
import { GuestFilterPicker } from '@/components/staff/GuestFilterPicker';

type BookingsSearchParams = {
  status?: string;
  from?: string;
  to?: string;
  guestId?: string;
  reference?: string;
};

const STATUSES = ['BOOKED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW'] as const;

export default async function BookingsPage({
  searchParams,
}: {
  searchParams: Promise<BookingsSearchParams>;
}) {
  const params = await searchParams;
  const { session, selected: property } = await requirePropertyContext('/console/bookings');
  if (!property) {
    // Unreachable in practice — the (app) layout already blocks rendering when the tenant has no
    // property. Fail loudly rather than render a page that assumes data it doesn't have.
    throw new Error('No active property resolved');
  }

  const query = new URLSearchParams({ propertyId: property.id });
  if (params.status) query.set('status', params.status);
  if (params.from) query.set('from', params.from);
  if (params.to) query.set('to', params.to);
  if (params.guestId) query.set('guestId', params.guestId);
  if (params.reference) query.set('reference', params.reference);

  const response = await upstream(`/api/v1/bookings?${query.toString()}`, {
    cookieHeader: session.cookieHeader,
  });
  const bookings: BookingResponse[] = response.ok
    ? BookingResponseSchema.array().parse(await response.json())
    : [];

  let selectedGuestName: string | undefined;
  if (params.guestId) {
    const guestResponse = await upstream(`/api/v1/guests/${params.guestId}`, {
      cookieHeader: session.cookieHeader,
    });
    if (guestResponse.ok) {
      selectedGuestName = GuestDtoSchema.parse(await guestResponse.json()).fullName;
    }
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">Bookings</h1>

      <form method="get" className="flex flex-wrap items-end gap-3 text-sm">
        <div>
          <label htmlFor="status" className="block text-xs font-medium text-text-muted">
            Status
          </label>
          <select
            id="status"
            name="status"
            defaultValue={params.status ?? ''}
            className="rounded-lg border border-border bg-surface px-2 py-1"
          >
            <option value="">All</option>
            {STATUSES.map((status) => (
              <option key={status} value={status}>
                {status}
              </option>
            ))}
          </select>
        </div>
        <div>
          {/* An overlap test, not equality (phase-6 §4.4) — this is every stay that touches the
              range, so the label says "staying" rather than "checking in". */}
          <label htmlFor="from" className="block text-xs font-medium text-text-muted">
            Staying from
          </label>
          <input
            id="from"
            name="from"
            type="date"
            defaultValue={params.from ?? ''}
            className="rounded-lg border border-border bg-surface px-2 py-1"
          />
        </div>
        <div>
          <label htmlFor="to" className="block text-xs font-medium text-text-muted">
            Staying to
          </label>
          <input
            id="to"
            name="to"
            type="date"
            defaultValue={params.to ?? ''}
            className="rounded-lg border border-border bg-surface px-2 py-1"
          />
        </div>
        <div>
          <label htmlFor="reference" className="block text-xs font-medium text-text-muted">
            Reference
          </label>
          <input
            id="reference"
            name="reference"
            type="text"
            defaultValue={params.reference ?? ''}
            className="rounded-lg border border-border bg-surface px-2 py-1"
          />
        </div>
        {params.guestId && <input type="hidden" name="guestId" value={params.guestId} />}
        <button type="submit" className="rounded-lg bg-accent px-3 py-1.5 text-sm font-semibold text-white">
          Filter
        </button>
      </form>

      <GuestFilterPicker selectedGuestName={selectedGuestName} />

      {bookings.length === 0 ? (
        <p className="text-sm text-text-muted">No bookings match these filters.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-text-muted">
                <th className="p-2">Reference</th>
                <th className="p-2">Guest</th>
                <th className="p-2">Dates</th>
                <th className="p-2">Nights</th>
                <th className="p-2">Room type</th>
                <th className="p-2">Status</th>
                <th className="p-2">Balance</th>
              </tr>
            </thead>
            <tbody>
              {bookings.map((booking) => (
                <tr key={booking.id} className="border-t border-border">
                  <td className="p-2">
                    <Link href={`/console/bookings/${booking.reference}`} className="text-accent hover:underline">
                      {booking.reference}
                    </Link>
                  </td>
                  <td className="p-2">{booking.guest.fullName}</td>
                  <td className="p-2">{formatStayRange(booking.checkIn, booking.checkOut)}</td>
                  <td className="p-2">{nightsBetween(booking.checkIn, booking.checkOut)}</td>
                  <td className="p-2">{booking.lines[0]?.roomTypeCode ?? '—'}</td>
                  <td className="p-2">
                    <StatusChip status={booking.status} />
                  </td>
                  <td className="p-2">
                    {formatMinor(booking.totalMinor - booking.amountPaidMinor, booking.currencyCode)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
