import Link from 'next/link';

const FEATURES = [
  {
    title: 'Knowledge base',
    body: 'Markdown or unstructured notes, up to 20,000 characters. Yours to edit, no onboarding call.',
  },
  {
    title: 'Escalation',
    body: "When a question isn't covered, it says so and hands over to a human. It does not guess a pet policy.",
  },
  {
    title: 'Latency',
    body: 'Answers land in about two seconds, with the model, token count and latency shown per reply.',
  },
] as const;

export function ConciergeDetailSection() {
  return (
    <section id="concierge" className="scroll-mt-24 border-t border-border py-16">
      <p className="text-xs font-medium text-text-muted">01</p>
      <div className="mt-2 flex flex-wrap items-center gap-3">
        <h2 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">Concierge</h2>
        <span className="rounded-full bg-accent-quiet px-2.5 py-1 text-xs font-medium uppercase tracking-wide text-accent">
          Live — try it
        </span>
      </div>
      <p className="mt-3 max-w-2xl text-lg leading-relaxed text-text-muted">
        It answers from rules you wrote, and it tells the guest when it doesn&apos;t know.
      </p>
      <p className="mt-3 max-w-2xl text-sm leading-relaxed text-text-muted">
        You keep a knowledge base in plain words — check-in times, dorm rates, ID rules, whether
        the kitchen closes. Edit a line and the next reply uses it. There is no save button and
        nothing to re-import.
      </p>

      <div className="mt-8 grid gap-6 sm:grid-cols-3">
        {FEATURES.map((feature) => (
          <div key={feature.title}>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">{feature.title}</p>
            <p className="mt-2 text-sm leading-relaxed text-foreground">{feature.body}</p>
          </div>
        ))}
      </div>

      <Link
        href="/concierge"
        className="mt-8 inline-block rounded-full bg-accent px-5 py-2.5 text-sm font-medium text-accent-foreground hover:opacity-90"
      >
        Try the concierge →
      </Link>

      <div className="mt-10 max-w-xl rounded-2xl border border-border bg-surface p-6">
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
          Example exchange — one rule you edit, one answer it produces
        </p>
        <pre className="mt-3 whitespace-pre-wrap rounded-lg bg-surface-muted p-3 text-xs text-text-muted">
          {'## Check-in & Check-out\n- Check-in: from 12:00 PM to 11:00 PM.\n- Check-out: strictly by 11:00 AM.'}
        </pre>
        <div className="mt-4 space-y-3 text-sm">
          <p>
            <span className="font-medium text-foreground">Guest —</span>{' '}
            <span className="text-text-muted">what time can I check in?</span>
          </p>
          <p>
            <span className="font-medium text-accent">Concierge —</span>{' '}
            <span className="text-text-muted">
              Check-in is from 12:00 PM to 11:00 PM. Check-out is by 11:00 AM, and please bring a
              government photo ID.
            </span>
          </p>
        </div>
      </div>
    </section>
  );
}
