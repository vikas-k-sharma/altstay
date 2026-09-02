import Link from 'next/link';

// phase-7 §5.1 block 1. Revised 2026-09-01, twice: v1 led with the commission stat as the H1,
// which read as "sign up, keep the ₹6-7L" — nothing live delivers that. v2 softened the claim but
// kept commission-recovery as the hero's spine, which doesn't survive a sharper objection: OTAs
// do two jobs, acquisition and transaction, and WhatsApp/the concierge only ever reaches guests
// who already have your number — it cannot acquire a first-time guest who found you via OTA
// search. That guest still books through the OTA regardless. So "recoverable commission" isn't
// really what AltStay sells; it's an unproven R2 hypothesis (roadmap §3's own kill criterion for
// that release), not a today claim. v3 drops commission from the hero entirely and leads with
// what's actually true right now: AltStay replaces the spreadsheet and the phone that are
// currently running the property, which is the correct competitive frame — against ill-fitting
// hotel PMSes (Cloudbeds, eZee, Hotelogix), not against the OTAs, which still handle discovery.
const REPLACES = [
  {
    from: 'The spreadsheet tracking which beds are free',
    to: 'Inventory model',
    status: 'In build',
  },
  {
    from: 'You, personally, answering WhatsApp at 2 AM',
    to: 'AI concierge',
    status: 'Live',
  },
  {
    from: "A hotel PMS that doesn't understand your rooms",
    to: 'Bookings & front desk',
    status: 'In build',
  },
] as const;

export function HeroSection() {
  return (
    <section className="mx-auto max-w-6xl px-4 pt-16 pb-20 sm:px-6 sm:pt-20">
      <p className="text-xs font-medium uppercase tracking-[0.08em] text-text-muted">
        Property management · Hostels · Homestays · Surf camps · Retreats
      </p>
      <h1 className="mt-4 max-w-4xl text-[2.5rem] font-semibold leading-[1.05] tracking-tight text-foreground sm:text-5xl lg:text-[3.5rem]">
        A PMS built for hostels, not hotels wearing a hostel skin.
      </h1>
      <p className="mt-6 max-w-2xl text-lg leading-relaxed text-text-muted">
        Your rooms sell two ways, your guests live on WhatsApp, and every hotel PMS pretends
        neither is true — whether you run a hostel, a homestay, a surf camp or a retreat centre.
        AltStay is built to replace the spreadsheet and the phone that are actually running your
        property today — starting with the phone.
      </p>

      <div className="mt-8 flex flex-wrap items-center gap-4">
        <Link
          href="/concierge"
          className="rounded-full bg-accent px-5 py-2.5 text-sm font-medium text-accent-foreground hover:opacity-90"
        >
          Try the concierge
        </Link>
        <Link
          href="/product"
          className="rounded-full border border-border px-5 py-2.5 text-sm font-medium text-foreground hover:bg-surface-muted"
        >
          See the product
        </Link>
      </div>
      <p className="mt-3 text-sm text-text-muted">No signup. No email field. It answers questions in the browser.</p>

      <div className="mt-12 max-w-lg rounded-2xl border border-border bg-surface p-6">
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">What this replaces</p>
        <ul className="mt-4 space-y-4">
          {REPLACES.map((item) => (
            <li key={item.to} className="flex items-start justify-between gap-4 text-sm">
              <span className="text-text-muted">{item.from}</span>
              <span className="flex shrink-0 items-center gap-2 text-right">
                <span className="font-medium text-foreground">{item.to}</span>
                <span
                  className={`rounded-full px-2 py-0.5 text-xs font-medium uppercase tracking-wide ${
                    item.status === 'Live'
                      ? 'bg-accent-quiet text-accent'
                      : 'bg-surface-muted text-text-muted'
                  }`}
                >
                  {item.status}
                </span>
              </span>
            </li>
          ))}
        </ul>
        <p className="mt-5 border-t border-border pt-4 text-xs text-text-muted">
          What this doesn&apos;t replace: Booking.com and Hostelworld still bring you guests who
          have never heard of you. AltStay runs the property once they arrive — it isn&apos;t a
          marketing channel.
        </p>
      </div>
    </section>
  );
}
