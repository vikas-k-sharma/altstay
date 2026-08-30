import { requirePropertyContext } from '@/lib/server/property';
import { upstream } from '@/lib/server/session';
import { RoomTypeDtoSchema } from '@/lib/contracts/inventory';
import { RatePlanDtoSchema, RateCalendarDtoSchema, type RateCalendarDto } from '@/lib/contracts/rate';
import { propertyToday, startOfMonth, endOfMonth } from '@/lib/staff/dates';
import { RateCalendarEditor } from '@/components/staff/RateCalendarEditor';

type RatesSearchParams = { ratePlanId?: string; month?: string };

export default async function RatesSettingsPage({
  searchParams,
}: {
  searchParams: Promise<RatesSearchParams>;
}) {
  const params = await searchParams;
  const { session, selected: property } = await requirePropertyContext('/console/settings/rates', [
    'OWNER',
    'MANAGER',
  ]);
  if (!property) {
    // Unreachable in practice — the (app) layout already blocks rendering when the tenant has no
    // property. Fail loudly rather than render a page that assumes data it doesn't have.
    throw new Error('No active property resolved');
  }

  const monthAnchor = params.month || propertyToday(property.timezone);
  const monthStart = startOfMonth(monthAnchor);
  const monthEnd = endOfMonth(monthAnchor);

  const [roomTypesResponse, ratePlansResponse] = await Promise.all([
    upstream(`/api/v1/properties/${property.slug}/room-types`, { cookieHeader: session.cookieHeader }),
    upstream(`/api/v1/properties/${property.slug}/rate-plans`, { cookieHeader: session.cookieHeader }),
  ]);
  const roomTypes = roomTypesResponse.ok ? RoomTypeDtoSchema.array().parse(await roomTypesResponse.json()) : [];
  const ratePlans = ratePlansResponse.ok ? RatePlanDtoSchema.array().parse(await ratePlansResponse.json()) : [];

  const selectedRatePlan =
    ratePlans.find((plan) => plan.id === params.ratePlanId) ?? ratePlans[0] ?? null;

  let calendar: RateCalendarDto[] = [];
  if (selectedRatePlan) {
    const calendarResponse = await upstream(
      `/api/v1/rate-plans/${selectedRatePlan.id}/calendar?from=${monthStart}&to=${monthEnd}`,
      { cookieHeader: session.cookieHeader }
    );
    calendar = calendarResponse.ok ? RateCalendarDtoSchema.array().parse(await calendarResponse.json()) : [];
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">Rates</h1>

      {ratePlans.length > 0 && (
        <form method="get" className="flex flex-wrap items-end gap-3 text-sm">
          <div>
            <label htmlFor="ratePlanId" className="block text-xs font-medium text-text-muted">
              Rate plan
            </label>
            <select
              id="ratePlanId"
              name="ratePlanId"
              defaultValue={selectedRatePlan?.id ?? ''}
              className="rounded-lg border border-border bg-surface px-2 py-1"
            >
              {ratePlans.map((plan) => {
                const rt = roomTypes.find((r) => r.id === plan.roomTypeId);
                return (
                  <option key={plan.id} value={plan.id}>
                    {rt?.code ?? plan.roomTypeId} · {plan.name}
                  </option>
                );
              })}
            </select>
          </div>
          <div>
            <label htmlFor="month" className="block text-xs font-medium text-text-muted">
              Month
            </label>
            <input
              id="month"
              name="month"
              type="date"
              defaultValue={monthStart}
              className="rounded-lg border border-border bg-surface px-2 py-1"
            />
          </div>
          <button type="submit" className="rounded-lg bg-accent px-3 py-1.5 text-sm font-semibold text-white">
            View
          </button>
        </form>
      )}

      <RateCalendarEditor
        propertySlug={property.slug}
        currencyCode={property.currencyCode}
        roomTypes={roomTypes}
        ratePlans={ratePlans}
        selectedRatePlan={selectedRatePlan}
        calendar={calendar}
        monthStart={monthStart}
        monthEnd={monthEnd}
      />
    </div>
  );
}
