import Link from 'next/link';
import { requirePropertyContext } from '@/lib/server/property';
import { upstream } from '@/lib/server/session';
import { FrontDeskResponseSchema } from '@/lib/contracts/booking';
import { PropertyAvailabilityResponseSchema } from '@/lib/contracts/availability';
import { propertyToday, addDays } from '@/lib/staff/dates';
import { FrontDeskRow } from '@/components/staff/FrontDeskRow';
import { QuickCheckInOutButton } from '@/components/staff/QuickCheckInOutButton';

export default async function ConsoleTodayPage() {
  const { session, selected: property } = await requirePropertyContext('/console');
  if (!property) {
    // Unreachable in practice — the (app) layout already blocks rendering when the tenant has no
    // property. Fail loudly rather than render a page that assumes data it doesn't have.
    throw new Error('No active property resolved');
  }

  // "Today" is always the property's own date (phase-5's front-desk endpoint javadoc), never the
  // server's or the browser's — and the console still sends the date it computed (rather than
  // omitting the param and trusting the backend default) so the header and the list can never
  // disagree (phase-6 §4.2).
  const today = propertyToday(property.timezone);
  const tomorrow = addDays(today, 1);

  const frontDeskResponse = await upstream(
    `/api/v1/properties/${property.slug}/front-desk?date=${today}`,
    { cookieHeader: session.cookieHeader }
  );
  const frontDesk = frontDeskResponse.ok
    ? FrontDeskResponseSchema.parse(await frontDeskResponse.json())
    : null;

  // Occupancy is not a field on FrontDeskResponse (phase-6 §1.3, §4.2) — it comes from a second
  // call, a one-night availability window, summed across room types. Never from counting
  // bookings, which is wrong the moment one booking holds more than one bed.
  const availabilityResponse = await upstream(
    `/api/v1/properties/${property.slug}/availability?from=${today}&to=${tomorrow}`,
    { cookieHeader: session.cookieHeader }
  );
  const availability = availabilityResponse.ok
    ? PropertyAvailabilityResponseSchema.parse(await availabilityResponse.json())
    : null;

  let availableTonight = 0;
  let totalTonight = 0;
  for (const roomType of availability?.roomTypes ?? []) {
    const day = roomType.days[0];
    if (day) {
      availableTonight += day.availableUnits;
      totalTonight += day.totalUnits;
    }
  }

  const arrivals = frontDesk?.arrivals ?? [];
  const departures = frontDesk?.departures ?? [];
  const inHouse = frontDesk?.inHouse ?? [];
  // There is no payment-recording endpoint anywhere in the delivered API (§12.1) — paymentState
  // never becomes anything but UNPAID in practice, so this currently matches every arrival. Kept
  // as specified rather than faked around: it becomes meaningful the moment that gap closes.
  const unpaidArrivals = arrivals.filter((booking) => booking.paymentState !== 'PAID');

  const dayIsEmpty = arrivals.length === 0 && departures.length === 0 && inHouse.length === 0;

  // Empty state is onboarding, not emptiness (§4.2, §10): "nothing arriving today" and "no
  // bookings at all" are different situations, and only the second needs the setup link. Only
  // checked when the day is otherwise empty, to avoid a third call on an ordinary busy day.
  let hasAnyBookingsEver = true;
  if (dayIsEmpty) {
    const anyBookingsResponse = await upstream(`/api/v1/bookings?propertyId=${property.id}`, {
      cookieHeader: session.cookieHeader,
    });
    if (anyBookingsResponse.ok) {
      // A plain existence check — nothing here is rendered, so a full schema parse buys nothing.
      const anyBookings: unknown = await anyBookingsResponse.json();
      hasAnyBookingsEver = Array.isArray(anyBookings) && anyBookings.length > 0;
    }
  }

  if (dayIsEmpty && !hasAnyBookingsEver) {
    return (
      <div className="rounded-lg border border-border bg-surface-muted p-4 text-sm">
        <p className="font-semibold">No bookings yet.</p>
        <p className="text-text-muted">
          Set up room types and spaces before the first guest can book.{' '}
          {/* Lands in slice 6. */}
          <Link href="/console/settings/inventory" className="text-accent hover:underline">
            Go to inventory setup
          </Link>
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">Today · {today}</h1>
        <Link href="/console/calendar" className="text-sm text-accent hover:underline">
          Calendar →
        </Link>
      </div>

      <section>
        <h2 className="font-semibold">Tonight</h2>
        <p className="text-sm text-text-muted">
          <Link href="/console/calendar" className="hover:underline">
            {availableTonight} / {totalTonight} units available
          </Link>
        </p>
      </section>

      <section>
        <h2 className="font-semibold">Arrivals</h2>
        {arrivals.length === 0 ? (
          <p className="text-sm text-text-muted">Nothing arriving today.</p>
        ) : (
          <table className="w-full text-sm">
            <tbody>
              {arrivals.map((booking) => (
                <FrontDeskRow
                  key={booking.id}
                  booking={booking}
                  action={
                    <QuickCheckInOutButton reference={booking.reference} status={booking.status} target="CHECKED_IN" />
                  }
                />
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section>
        <h2 className="font-semibold">Departures</h2>
        {departures.length === 0 ? (
          <p className="text-sm text-text-muted">No departures today.</p>
        ) : (
          <table className="w-full text-sm">
            <tbody>
              {departures.map((booking) => (
                <FrontDeskRow
                  key={booking.id}
                  booking={booking}
                  action={
                    <QuickCheckInOutButton reference={booking.reference} status={booking.status} target="CHECKED_OUT" />
                  }
                />
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section>
        <h2 className="font-semibold">In house</h2>
        {inHouse.length === 0 ? (
          <p className="text-sm text-text-muted">No one in house right now.</p>
        ) : (
          <table className="w-full text-sm">
            <tbody>
              {inHouse.map((booking) => (
                <FrontDeskRow key={booking.id} booking={booking} />
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section>
        <h2 className="font-semibold">Unpaid</h2>
        {unpaidArrivals.length === 0 ? (
          <p className="text-sm text-text-muted">Nothing outstanding among today&apos;s arrivals.</p>
        ) : (
          <table className="w-full text-sm">
            <tbody>
              {unpaidArrivals.map((booking) => (
                <FrontDeskRow key={booking.id} booking={booking} />
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
