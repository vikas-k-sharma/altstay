import Link from 'next/link';
import { notFound } from 'next/navigation';
import { requirePropertyContext } from '@/lib/server/property';
import { upstream } from '@/lib/server/session';
import { BookingResponseSchema } from '@/lib/contracts/booking';
import { formatMinor } from '@/lib/staff/money';
import { formatStayRange, nightsBetween } from '@/lib/staff/dates';
import { StatusChip } from '@/components/staff/StatusChip';
import { BookingTransitionActions } from '@/components/staff/BookingTransitionActions';

export default async function BookingDetailPage({
  params,
}: {
  params: Promise<{ reference: string }>;
}) {
  const { reference } = await params;
  const { session } = await requirePropertyContext(`/console/bookings/${reference}`);

  const response = await upstream(`/api/v1/bookings/${reference}`, {
    cookieHeader: session.cookieHeader,
  });
  if (response.status === 404) {
    notFound();
  }
  const booking = BookingResponseSchema.parse(await response.json());

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="text-xl font-bold">{booking.reference}</h1>
          <StatusChip status={booking.status} />
        </div>
        <Link href="/console/bookings" className="text-sm text-accent hover:underline">
          ← Bookings
        </Link>
      </div>

      <BookingTransitionActions reference={booking.reference} status={booking.status} />

      <section>
        <h2 className="font-semibold">Guest</h2>
        <p>{booking.guest.fullName}</p>
        <p className="text-sm text-text-muted">
          {booking.guest.email ?? '—'} · {booking.guest.phone ?? '—'}
        </p>
      </section>

      <section>
        <h2 className="font-semibold">Stay</h2>
        <p>
          {formatStayRange(booking.checkIn, booking.checkOut)} ·{' '}
          {nightsBetween(booking.checkIn, booking.checkOut)} night(s)
        </p>
        <p className="text-sm text-text-muted">
          {booking.adults} adult(s), {booking.children} child(ren) · via {booking.source}
        </p>
        {booking.earlyCheckIn && <p className="text-sm text-warning">Note: early check-in</p>}
      </section>

      <section>
        <h2 className="font-semibold">Lines</h2>
        <ul className="space-y-1 text-sm">
          {booking.lines.map((line) => (
            <li key={line.id}>
              {line.roomTypeCode} · {formatStayRange(line.checkIn, line.checkOut)} · {line.unitCount} unit(s) ·{' '}
              {formatMinor(line.amountMinor, booking.currencyCode)}
            </li>
          ))}
        </ul>
      </section>

      <section>
        {/* Not "bed history": allocations come from current lines only, so a bed released by a
            date modification (a superseded line) drops out (phase-6 §1.3, §4.5). */}
        <h2 className="font-semibold">Beds on this booking</h2>
        <ul className="space-y-1 text-sm">
          {booking.allocations.map((allocation) => (
            <li key={allocation.id} className={allocation.releasedAt ? 'text-text-muted line-through' : ''}>
              {allocation.unitLabel} · {formatStayRange(allocation.checkIn, allocation.checkOut)}
              {allocation.releasedAt && (
                <span className="no-underline"> · released {new Date(allocation.releasedAt).toLocaleString()}</span>
              )}
            </li>
          ))}
        </ul>
      </section>

      <section>
        <h2 className="font-semibold">Money</h2>
        <p>
          Subtotal {formatMinor(booking.subtotalMinor, booking.currencyCode)} + Tax{' '}
          {formatMinor(booking.taxMinor, booking.currencyCode)} = Total{' '}
          {formatMinor(booking.totalMinor, booking.currencyCode)}
        </p>
        <p className="text-sm text-text-muted">
          Paid {formatMinor(booking.amountPaidMinor, booking.currencyCode)} · {booking.paymentState}
        </p>
      </section>

      <section>
        <h2 className="font-semibold">History</h2>
        <ul className="space-y-1 text-sm">
          {booking.statusHistory.map((entry) => (
            <li key={entry.id}>
              {entry.fromStatus ?? 'created'} → {entry.toStatus} · {new Date(entry.changedAt).toLocaleString()}
              {entry.reason ? ` · ${entry.reason}` : ''}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
