import Link from 'next/link';

// phase-7 §0 — the block a KILL verdict reorders. Each pillar's copy lives in this one array so
// re-pointing (concierge demoted, inventory/bookings first) is a data edit, not a rebuild.
// 2026-09-05: the inventory pillar said "beds and rooms", which names the hostel case and hides
// the general one. The schema underneath is already generic — a space is sold WHOLE or PER_UNIT
// (V7__inventory.sql) — so a cottage, a tent and a homestay room are the same model, not a
// roadmap item. The copy now matches the schema instead of the launch example.
export const PILLARS = [
  {
    id: 'concierge',
    status: 'Live',
    title: 'Answers guests from rules you wrote',
    body: "Paste your check-in times, rates and house rules in plain words. Guests get an answer in seconds, and when a question isn't covered it says so and hands over to you instead of inventing a pet policy.",
  },
  {
    id: 'inventory',
    status: 'In build',
    title: 'Beds, rooms and whole spaces in one model',
    body: 'Room types, physical spaces and the beds inside them, so a space sold whole and the units inside it can never both be available — a dorm and its beds, a tent and its bunks, a cottage let whole. This is the part no hotel PMS does.',
  },
  {
    id: 'bookings',
    status: 'In build',
    title: 'A front desk, not a spreadsheet',
    body: 'Availability by date, a booking from enquiry to check-out, rates that change by date, and check-in and check-out that whoever is on the desk can run on a laptop.',
  },
] as const;

export function PillarsSection() {
  return (
    <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h2 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
          Three parts, built in this order.
        </h2>
        <Link href="/product" className="text-sm font-medium text-accent hover:opacity-80">
          All three in detail →
        </Link>
      </div>

      <div className="mt-10 grid gap-6 sm:grid-cols-3">
        {PILLARS.map((pillar) => (
          <div key={pillar.id} className="flex flex-col rounded-2xl border border-border bg-surface p-6">
            <span
              className={`w-fit rounded-full px-2.5 py-1 text-xs font-medium uppercase tracking-wide ${
                pillar.status === 'Live'
                  ? 'bg-accent-quiet text-accent'
                  : 'bg-surface-muted text-text-muted'
              }`}
            >
              {pillar.status}
            </span>
            <h3 className="mt-4 text-lg font-semibold text-foreground">{pillar.title}</h3>
            <p className="mt-2 flex-1 text-sm leading-relaxed text-text-muted">{pillar.body}</p>
            <Link
              href={`/product#${pillar.id}`}
              className="mt-4 text-sm font-medium text-accent hover:opacity-80"
            >
              {pillar.id.charAt(0).toUpperCase() + pillar.id.slice(1)} →
            </Link>
          </div>
        ))}
      </div>
    </section>
  );
}
