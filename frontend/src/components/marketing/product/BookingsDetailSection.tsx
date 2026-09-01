const FEATURES = [
  {
    title: 'Calendar',
    body: 'Free beds per date, with the hybrid rule already applied — no second sheet to check.',
  },
  {
    title: 'Lifecycle',
    body: 'Booked → checked in → checked out, plus cancelled and no-show, each with a time on it.',
  },
  {
    title: 'Rates',
    body: 'Per date, per room type, set in the console — the concierge quotes the same number.',
  },
] as const;

const AVAILABILITY_ROWS = [
  { unit: 'Room 3 · dorm beds', values: ['6', '2', '2', '5', '4', 'held', 'held'] },
  { unit: 'Room 3 · whole', values: ['free', 'closed', 'closed', 'closed', 'closed', 'SOLD', 'SOLD'] },
  { unit: 'Sea-view private', values: ['free', 'free', 'SOLD', 'SOLD', 'free', 'free', 'free'] },
] as const;

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const;

const LIFECYCLE_STATES = ['Booked', 'Checked in', 'Checked out', 'Cancelled', 'No show'] as const;

// phase-7 §5.2, §3 — the front desk isn't built yet, so this is the design, stated as such, not
// a screenshot of a UI state that has never rendered.
export function BookingsDetailSection() {
  return (
    <section id="bookings" className="scroll-mt-24 border-t border-border py-16">
      <p className="text-xs font-medium text-text-muted">03</p>
      <div className="mt-2 flex flex-wrap items-center gap-3">
        <h2 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
          Bookings &amp; front desk
        </h2>
        <span className="rounded-full bg-surface-muted px-2.5 py-1 text-xs font-medium uppercase tracking-wide text-text-muted">
          In build
        </span>
      </div>
      <p className="mt-3 max-w-2xl text-lg leading-relaxed text-text-muted">
        Availability by date, a booking with a lifecycle, and a desk your staff can run on a
        laptop.
      </p>
      <p className="mt-3 max-w-2xl text-sm leading-relaxed text-text-muted">
        One calendar showing what is genuinely free, per date and per unit. A booking that moves
        from held to checked in to checked out, with the state written down rather than
        remembered. Rates that differ by date, because a Saturday in December is not a Tuesday in
        June.
      </p>

      <div className="mt-8 grid gap-6 sm:grid-cols-3">
        {FEATURES.map((feature) => (
          <div key={feature.title}>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">{feature.title}</p>
            <p className="mt-2 text-sm leading-relaxed text-foreground">{feature.body}</p>
          </div>
        ))}
      </div>

      <p className="mt-10 text-sm italic text-text-muted">
        No screenshot here yet: the front desk is being built. This is the design, not a
        photograph of it.
      </p>

      <div className="mt-4 overflow-x-auto rounded-2xl border border-border">
        <table className="w-full min-w-[560px] border-collapse text-sm">
          <caption className="sr-only">Availability, week of 13 October, beds free</caption>
          <thead>
            <tr className="border-b border-border bg-surface-muted text-left text-xs uppercase tracking-wide text-text-muted">
              <th scope="col" className="px-3 py-2">
                Unit
              </th>
              {DAYS.map((day) => (
                <th key={day} scope="col" className="px-3 py-2">
                  {day}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {AVAILABILITY_ROWS.map((row) => (
              <tr key={row.unit} className="border-b border-border last:border-0">
                <th scope="row" className="px-3 py-2 text-left font-medium text-foreground">
                  {row.unit}
                </th>
                {row.values.map((value, i) => (
                  <td
                    key={i}
                    className={`px-3 py-2 ${
                      value === 'SOLD' || value === 'closed' || value === 'held'
                        ? 'text-text-muted'
                        : 'text-foreground'
                    }`}
                  >
                    {value}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mt-3 text-xs text-text-muted">
        Numbers are beds free. &quot;Closed&quot; and &quot;held&quot; are the hybrid rule doing
        its job — each cell says why in words, not by colour.
      </p>

      <div className="mt-8 flex flex-wrap gap-2">
        {LIFECYCLE_STATES.map((state) => (
          <span
            key={state}
            className="rounded-full border border-border px-3 py-1 text-xs font-medium text-text-muted"
          >
            {state}
          </span>
        ))}
      </div>
    </section>
  );
}
