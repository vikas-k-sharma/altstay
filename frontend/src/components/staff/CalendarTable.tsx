import Link from 'next/link';
import { formatMinor } from '@/lib/staff/money';
import type { RoomTypeAvailabilityDto } from '@/lib/contracts/availability';

/**
 * No client-side state is needed here — every cell is a plain link and the date range comes from
 * the URL — so this stays a Server Component despite §3's route map calling for a "Client grid".
 * Deliberate simplification, recorded in phase-6-staff-console.md §12.1.
 */
export function CalendarTable({
  roomTypes,
  currency,
}: {
  roomTypes: RoomTypeAvailabilityDto[];
  currency: string;
}) {
  const dates = roomTypes[0]?.days.map((day) => day.date) ?? [];

  return (
    <div className="overflow-x-auto rounded-lg border border-border">
      <table className="border-collapse text-sm">
        <thead>
          <tr>
            <th className="sticky left-0 top-0 z-20 min-w-[10rem] border-b border-r border-border bg-surface p-2 text-left">
              Room type
            </th>
            {dates.map((date) => (
              <th
                key={date}
                className="sticky top-0 z-10 min-w-[6rem] border-b border-border bg-surface p-2 text-left font-medium text-text-muted"
              >
                {formatColumnHeader(date)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {roomTypes.map((roomType) => (
            <tr key={roomType.roomTypeId} className="border-t border-border">
              <th className="sticky left-0 z-10 border-r border-border bg-surface p-2 text-left font-medium">
                {roomType.code}
                {roomType.saleMode === 'WHOLE' && (
                  <div className="text-xs font-normal text-text-muted">
                    {roomType.bookableWholeSpaces} bookable whole
                  </div>
                )}
              </th>
              {roomType.days.map((day) => {
                const [available, total] =
                  roomType.saleMode === 'WHOLE'
                    ? [day.availableSpaces, day.totalSpaces]
                    : [day.availableUnits, day.totalUnits];
                const isSoldOut = available === 0;

                return (
                  <td key={day.date} className="border-t border-border p-0">
                    <Link
                      href={`/console/bookings/new?roomTypeId=${roomType.roomTypeId}&date=${day.date}`}
                      className={`block p-2 hover:bg-surface-muted ${isSoldOut ? 'text-text-muted' : ''}`}
                    >
                      <div className="font-semibold">
                        {available} / {total}
                      </div>
                      <div className="text-xs text-text-muted">{formatMinor(day.rateMinor, currency)}</div>
                    </Link>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function formatColumnHeader(date: string): string {
  const [, month, day] = date.split('-').map(Number);
  return new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric' }).format(
    new Date(2000, month - 1, day)
  );
}
