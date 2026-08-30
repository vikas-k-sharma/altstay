import { requirePropertyContext } from '@/lib/server/property';
import { upstream } from '@/lib/server/session';
import { PropertyAvailabilityResponseSchema } from '@/lib/contracts/availability';
import { propertyToday, addDays } from '@/lib/staff/dates';
import { BookingWizard } from '@/components/staff/BookingWizard';

type NewBookingSearchParams = { from?: string; to?: string; date?: string; roomTypeId?: string };

export default async function NewBookingPage({
  searchParams,
}: {
  searchParams: Promise<NewBookingSearchParams>;
}) {
  const params = await searchParams;
  const { session, selected: property } = await requirePropertyContext('/console/bookings/new');
  if (!property) {
    // Unreachable in practice — the (app) layout already blocks rendering when the tenant has no
    // property. Fail loudly rather than render a page that assumes data it doesn't have.
    throw new Error('No active property resolved');
  }

  const today = propertyToday(property.timezone);

  // "Dates live in the URL" (§4.6) so a refresh or a shared link lands in the same place. Two
  // shapes reach here: a calendar cell click carries a single `date` (§4.3) as a check-in
  // suggestion, still needing check-out/adults/children; a shared or refreshed wizard link
  // carries a full `from`/`to` range and skips straight to ROOM.
  const startAtRoom = Boolean(params.from && params.to);
  const initialCheckIn = params.from || params.date || today;
  const initialCheckOut = params.to || addDays(initialCheckIn, 1);

  let availability = null;
  if (startAtRoom) {
    const response = await upstream(
      `/api/v1/properties/${property.slug}/availability?from=${initialCheckIn}&to=${initialCheckOut}`,
      { cookieHeader: session.cookieHeader }
    );
    availability = response.ok ? PropertyAvailabilityResponseSchema.parse(await response.json()) : null;
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">New booking</h1>
      <BookingWizard
        property={{ id: property.id }}
        today={today}
        initialCheckIn={initialCheckIn}
        initialCheckOut={initialCheckOut}
        initialRoomTypeId={params.roomTypeId ?? null}
        availability={availability}
        startAtRoom={startAtRoom}
      />
    </div>
  );
}
