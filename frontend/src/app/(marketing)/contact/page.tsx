import type { Metadata } from 'next';

export const dynamic = 'force-static';

export const metadata: Metadata = {
  title: 'Contact',
  description:
    'No contact form — two real ways to reach a person: WhatsApp for the fastest route, email for anything longer.',
};

// phase-7 §10 slice 1 — shell only. Full page (§5.4, §6) lands in slice 4.
export default function ContactPage() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
      <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">Contact</h1>
      <p className="mt-4 max-w-2xl text-lg text-text-muted">The full page lands in the next slice.</p>
    </div>
  );
}
