import { requirePropertyContext } from '@/lib/server/property';
import { upstream } from '@/lib/server/session';
import { AmenityResponseSchema } from '@/lib/contracts/amenity';
import { PropertySettingsForm } from '@/components/staff/PropertySettingsForm';

export default async function PropertySettingsPage() {
  const { session, selected: property } = await requirePropertyContext('/console/settings/property', [
    'OWNER',
  ]);
  if (!property) {
    // Unreachable in practice — the (app) layout already blocks rendering when the tenant has no
    // property. Fail loudly rather than render a page that assumes data it doesn't have.
    throw new Error('No active property resolved');
  }

  const response = await upstream('/api/v1/amenities', { cookieHeader: session.cookieHeader });
  const amenities = response.ok ? AmenityResponseSchema.array().parse(await response.json()) : [];

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">Property settings</h1>
      <PropertySettingsForm property={property} amenities={amenities} />
    </div>
  );
}
