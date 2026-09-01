import type { Metadata } from 'next';
import { CONTACT_EMAIL, WHATSAPP_DISPLAY, WHATSAPP_HREF } from '@/lib/marketing/contact';

export const dynamic = 'force-static';

const TITLE = 'Contact';
const DESCRIPTION =
  'No contact form — two real ways to reach a person: WhatsApp for the fastest route, email for anything longer.';

export const metadata: Metadata = {
  title: TITLE,
  description: DESCRIPTION,
  alternates: { canonical: '/contact' },
  openGraph: { title: TITLE, description: DESCRIPTION, url: '/contact', type: 'website' },
  twitter: { card: 'summary_large_image', title: TITLE, description: DESCRIPTION },
};

// phase-7 §5.4, §6 — no form, deliberately. mailto + WhatsApp only.
export default function ContactPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-16 sm:px-6">
      <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Contact</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">Contact</h1>
      <p className="mt-4 max-w-2xl text-lg leading-relaxed text-text-muted">
        There&apos;s no form here. Two ways to reach an actual person.
      </p>
      <p className="mt-3 max-w-2xl text-sm leading-relaxed text-text-muted">
        A form would put your message in a queue I&apos;d have to check. WhatsApp is how this
        business runs and how support will work, so it&apos;s the fastest route to me — the same
        way your guests reach you.
      </p>

      <div className="mt-10 grid gap-6 sm:grid-cols-2">
        <div className="rounded-2xl border border-border bg-surface p-6">
          <p className="text-xs font-medium uppercase tracking-wide text-accent">WhatsApp · Fastest</p>
          <p className="mt-2 text-lg font-medium text-foreground">{WHATSAPP_DISPLAY}</p>
          <p className="mt-3 text-sm leading-relaxed text-text-muted">
            Voice notes are fine. If you send me the five questions your guests ask most,
            I&apos;ll load them into the concierge and send you what it answers.
          </p>
          <a
            href={WHATSAPP_HREF}
            className="mt-4 inline-block rounded-full bg-accent px-5 py-2.5 text-sm font-medium text-accent-foreground hover:opacity-90"
          >
            Open WhatsApp
          </a>
        </div>

        <div className="rounded-2xl border border-border bg-surface p-6">
          <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Email · For longer things</p>
          <p className="mt-2 text-lg font-medium text-foreground">{CONTACT_EMAIL}</p>
          <p className="mt-3 text-sm leading-relaxed text-text-muted">
            Better if you&apos;re sending a rate sheet, a room list, or a long description of how
            your property is laid out. Opens in your mail app — nothing is stored here.
          </p>
          <a
            href={`mailto:${CONTACT_EMAIL}`}
            className="mt-4 inline-block rounded-full border border-border px-5 py-2.5 text-sm font-medium text-foreground hover:bg-surface-muted"
          >
            Write an email
          </a>
        </div>
      </div>

      <div className="mt-10 space-y-6 border-t border-border pt-8">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Response time</p>
          <p className="mt-2 max-w-xl text-sm leading-relaxed text-text-muted">
            Usually within a few hours, 9 AM to 9 PM IST. Overnight if you message late. If a day
            passes, message again — it means I missed it, not that I&apos;m screening you.
          </p>
        </div>

        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-text-muted">What&apos;s useful to send</p>
          <p className="mt-2 max-w-xl text-sm leading-relaxed text-text-muted">
            Bed count, how many rooms flip between dorm and private, and which OTAs you&apos;re
            on. Three lines is enough to tell whether this fits.
          </p>
        </div>

        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Already a staff user?</p>
          <p className="mt-2 max-w-xl text-sm leading-relaxed text-text-muted">
            The console is at <span className="text-foreground">altstay.in/console</span>.
            Front-desk problems go on WhatsApp too — same number.
          </p>
        </div>
      </div>
    </div>
  );
}
