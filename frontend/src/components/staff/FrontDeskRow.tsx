import Link from 'next/link';
import type { ReactNode } from 'react';
import { StatusChip } from './StatusChip';
import { formatMinor } from '@/lib/staff/money';
import type { BookingResponse } from '@/lib/contracts/booking';

/**
 * One row shared by the Today screen's Arrivals/Departures/In house/Unpaid panels. Room type
 * comes from `lines[]`, not `allocations[]` — the front-desk endpoint's summary bookings always
 * carry an empty `allocations[]` (`BookingService.summaryOf`), so bed labels aren't available
 * here at all, only room type and unit count.
 */
export function FrontDeskRow({ booking, action }: { booking: BookingResponse; action?: ReactNode }) {
  const roomTypeSummary =
    booking.lines.map((line) => `${line.roomTypeCode} ×${line.unitCount}`).join(', ') || '—';
  const balanceMinor = booking.totalMinor - booking.amountPaidMinor;

  return (
    <tr className="border-t border-border">
      <td className="p-2">
        <Link href={`/console/bookings/${booking.reference}`} className="text-accent hover:underline">
          {booking.reference}
        </Link>
      </td>
      <td className="p-2">{booking.guest.fullName}</td>
      <td className="p-2">{roomTypeSummary}</td>
      <td className="p-2">
        <StatusChip status={booking.status} />
      </td>
      <td className="p-2">{formatMinor(balanceMinor, booking.currencyCode)}</td>
      <td className="p-2">{action}</td>
    </tr>
  );
}
