const FEATURES = [
  {
    title: 'Room type',
    body: "6-bed mixed AC dorm, Garden Cottage, Beachfront tent · what it costs, what it includes, how it's described.",
  },
  {
    title: 'Space',
    body: 'Room 3, Tent 4, the cottage · a real door with a real key, sellable whole or by what is inside it.',
  },
  {
    title: 'Unit',
    body: 'Bed 3-A through 3-F · the smallest thing a guest can buy, and what a whole-space booking holds all of.',
  },
] as const;

// The same three layers, mapped onto property types other than the launch example. Not a roadmap
// item: the schema is already this general — `space.sale_mode` is WHOLE or PER_UNIT and a room
// type carries a `kind` (V7__inventory.sql), so a homestay room is a space with one unit sold
// whole, and a tent is a space with four sold either way. Written out because "a bed in a dorm"
// was the only example on the page and read as the only case supported.
const ELSEWHERE = [
  {
    property: 'Surf camp',
    body: 'Beachfront tent → Tent 4 → bunks 4-A to 4-D. Sold per bunk midweek, whole to a couple on Saturday.',
  },
  {
    property: 'Homestay',
    body: 'Garden double → the garden room → one unit, sold whole. Two of the three layers, same model, nothing to work around.',
  },
  {
    property: 'Retreat centre',
    body: 'Shared twin → Cottage 2 → two beds. Per bed during a course, whole when a family takes it.',
  },
] as const;

// phase-7 §5.2 — the layer a hotel PMS is missing. Static hierarchy, not a screenshot: the
// inventory model is in build, so this shows the data model, not a rendered UI.
export function InventoryDetailSection() {
  return (
    <section id="inventory" className="scroll-mt-24 border-t border-border py-16">
      <p className="text-xs font-medium text-text-muted">02</p>
      <div className="mt-2 flex flex-wrap items-center gap-3">
        <h2 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">Inventory</h2>
        <span className="rounded-full bg-surface-muted px-2.5 py-1 text-xs font-medium uppercase tracking-wide text-text-muted">
          In build
        </span>
      </div>
      <p className="mt-3 max-w-2xl text-lg leading-relaxed text-text-muted">
        Room types, physical spaces, bed units — and the hybrid case as the point, not an edge
        case.
      </p>
      <p className="mt-3 max-w-2xl text-sm leading-relaxed text-text-muted">
        A hotel PMS gives you room types and rooms. That is one layer short: the thing you sell in
        a dorm or a tent is a bed, and the thing you sell on a weekend is the whole space those
        beds are in. AltStay models all three, so the two ways of selling the same space stay in
        sync without you holding it together. A homestay room or a whole cottage uses the same
        model with one unit instead of six — nothing to work around, and nothing extra to set up.
      </p>

      <div className="mt-8 grid gap-6 sm:grid-cols-3">
        {FEATURES.map((feature) => (
          <div key={feature.title}>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">{feature.title}</p>
            <p className="mt-2 text-sm leading-relaxed text-foreground">{feature.body}</p>
          </div>
        ))}
      </div>

      <div className="mt-10 max-w-xl rounded-2xl border border-border bg-surface p-6">
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
          Three layers, one set of units
        </p>
        <div className="mt-4 space-y-2 text-sm text-text-muted">
          <p className="font-medium text-foreground">6-bed Mixed AC Dorm — room type · ₹650/bed</p>
          <p className="pl-4">└ contains</p>
          <p className="pl-4 font-medium text-foreground">Room 3 — en-suite, ground floor — also ₹2,400 whole</p>
          <p className="pl-8">└ contains</p>
          <p className="pl-8">bed 3-A · bed 3-B · bed 3-C · bed 3-D · bed 3-E · bed 3-F</p>
        </div>
        <div className="mt-4 space-y-1 border-t border-border pt-4 text-sm text-text-muted">
          <p>Sell bed 3-A for Tue → private double closes for Tue</p>
          <p>Sell Room 3 whole for Sat → all six beds held for Sat</p>
        </div>
      </div>

      <div className="mt-8 max-w-2xl">
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
          The same three layers, in a property that isn&apos;t a hostel
        </p>
        <dl className="mt-4 space-y-3">
          {ELSEWHERE.map((item) => (
            <div key={item.property} className="sm:flex sm:gap-4">
              <dt className="text-sm font-medium text-foreground sm:w-36 sm:shrink-0">{item.property}</dt>
              <dd className="mt-1 text-sm leading-relaxed text-text-muted sm:mt-0">{item.body}</dd>
            </div>
          ))}
        </dl>
      </div>

      <p className="mt-8 text-sm font-medium text-foreground">
        Overselling isn&apos;t discipline. It&apos;s arithmetically impossible.
      </p>
    </section>
  );
}
