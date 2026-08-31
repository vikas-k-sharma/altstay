import Link from 'next/link';
import { LiveDemoFrame } from './LiveDemoFrame';

// phase-7 §5.1 block 4, §3 — "the demo is the social proof." Embedding the real, live route is
// stronger evidence than a screenshot of it: nothing here has ever not rendered. Click-to-load
// (LiveDemoFrame) rather than an eager iframe — see that file for why.
export function DemoSection() {
  return (
    <section className="border-t border-border bg-surface-muted">
      <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">The concierge, running</p>
        <h2 className="mt-3 max-w-2xl text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
          This is the real thing, not a mockup.
        </h2>
        <p className="mt-3 max-w-2xl text-sm leading-relaxed text-text-muted">
          Ask it something below, or open it full-screen. Edit a rule on the right and the next
          answer on the left uses it — no save button, no restart, no import.
        </p>

        <div className="mt-8 overflow-hidden rounded-2xl border border-border shadow-sm">
          <LiveDemoFrame />
        </div>

        <Link href="/concierge" className="mt-4 inline-block text-sm font-medium text-accent hover:opacity-80">
          Open the demo full-screen →
        </Link>
      </div>
    </section>
  );
}
