import { requirePropertyContext } from '@/lib/server/property';
import { upstream } from '@/lib/server/session';
import { RoomTypeDtoSchema, SpaceDtoSchema } from '@/lib/contracts/inventory';
import { RoomTypesEditor } from '@/components/staff/RoomTypesEditor';
import { SpacesEditor } from '@/components/staff/SpacesEditor';
import { HybridMappingEditor } from '@/components/staff/HybridMappingEditor';

export default async function InventorySettingsPage() {
  const { session, selected: property } = await requirePropertyContext('/console/settings/inventory', [
    'OWNER',
    'MANAGER',
  ]);
  if (!property) {
    // Unreachable in practice — the (app) layout already blocks rendering when the tenant has no
    // property. Fail loudly rather than render a page that assumes data it doesn't have.
    throw new Error('No active property resolved');
  }

  const [roomTypesResponse, spacesResponse] = await Promise.all([
    upstream(`/api/v1/properties/${property.slug}/room-types`, { cookieHeader: session.cookieHeader }),
    upstream(`/api/v1/properties/${property.slug}/spaces`, { cookieHeader: session.cookieHeader }),
  ]);

  const roomTypes = roomTypesResponse.ok ? RoomTypeDtoSchema.array().parse(await roomTypesResponse.json()) : [];
  const spaces = spacesResponse.ok ? SpaceDtoSchema.array().parse(await spacesResponse.json()) : [];

  return (
    <div className="space-y-8">
      <h1 className="text-xl font-bold">Inventory</h1>

      {/* Three editors, in the order an owner sets a property up (phase-6 §4.8). */}
      <section className="space-y-2">
        <h2 className="font-semibold">Room types</h2>
        <RoomTypesEditor propertySlug={property.slug} currencyCode={property.currencyCode} roomTypes={roomTypes} />
      </section>

      <section className="space-y-2">
        <h2 className="font-semibold">Spaces and units</h2>
        <SpacesEditor propertySlug={property.slug} spaces={spaces} />
      </section>

      <section className="space-y-2">
        <h2 className="font-semibold">Mapping — what each room can be sold as</h2>
        <HybridMappingEditor spaces={spaces} roomTypes={roomTypes} />
      </section>
    </div>
  );
}
