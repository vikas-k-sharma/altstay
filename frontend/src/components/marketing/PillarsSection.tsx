import Link from 'next/link';

// phase-7 §0 — the block a KILL verdict reorders. Each pillar's copy lives in this one array so
// re-pointing (concierge demoted, inventory/bookings first) is a data edit, not a rebuild.
export const PILLARS = [
  {
    id: 'concierge',
    status: 'Live',
    title: 'Answers guests from rules you wrote',
    body: "Paste your check-in times, rates and policies in plain words. Guests get an answer in seconds, and when a question isn't covered it says so and hands over to you instead of inventing a pet policy.",
  },
  {
    id: 'inventory',
    status: 'In build',
    title: 'Beds and rooms as the same thing',
    body: 'Room types, physical rooms and bed-level units in one model, so a room sold whole and the beds inside it can never both be available. This is the part no hotel PMS does.',
  },
  {
    id: 'bookings',
    status: 'In build',
    title: 'A front desk, not a spreadsheet',
    body: 'Availability by date, a booking from enquiry to check-out, rates that change by date, and check-in and check-out that a staff member can run on a laptop at the desk.',
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
