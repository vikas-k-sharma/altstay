import { CONTACT_EMAIL, WHATSAPP_HREF } from '@/lib/marketing/contact';

// phase-7 §5.1 block 6 — where the product actually is, stated plainly, and one way to get in
// touch. Status wording matches CLAUDE.md's Phase 3 record: sessions scheduled, gate undecided.
const STATUS_ITEMS = [
  {
    label: 'Live',
    body: 'The concierge. You can use it right now, on a sample property or your own rules.',
  },
  {
    label: 'In build',
    body: 'Inventory and bookings — the model, the availability calendar, and the front desk.',
  },
  {
    label: 'Not yet',
    body: 'Customers. No one is paying for this yet — two beta sessions are scheduled for October 2026.',
  },
] as const;

export function StatusCtaSection() {
  return (
    <section className="border-t border-border bg-surface-muted">
      <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Where this actually is</p>

        <dl className="mt-6 grid gap-6 sm:grid-cols-3">
          {STATUS_ITEMS.map((item) => (
            <div key={item.label}>
              <dt className="text-sm font-semibold text-foreground">{item.label}</dt>
              <dd className="mt-1 text-sm leading-relaxed text-text-muted">{item.body}</dd>
            </div>
          ))}
        </dl>

        <div className="mt-12 max-w-2xl border-t border-border pt-8">
          <p className="text-lg font-medium text-foreground">
            If you run a property like this, I want the questions your guests actually ask.
          </p>
          <p className="mt-2 text-sm leading-relaxed text-text-muted">
            That list is what the concierge is tuned against, and it&apos;s more useful to me than
            a signup. Half an hour on WhatsApp, no pitch.
          </p>
          <div className="mt-6 flex flex-wrap gap-4">
            <a
              href={WHATSAPP_HREF}
              className="rounded-full bg-accent px-5 py-2.5 text-sm font-medium text-accent-foreground hover:opacity-90"
            >
              Message on WhatsApp
            </a>
            <a
              href={`mailto:${CONTACT_EMAIL}`}
              className="rounded-full border border-border px-5 py-2.5 text-sm font-medium text-foreground hover:bg-surface"
            >
              {CONTACT_EMAIL}
            </a>
          </div>
        </div>
      </div>
    </section>
  );
}
