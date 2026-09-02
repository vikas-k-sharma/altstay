import type { Metadata } from 'next';
import Link from 'next/link';

export const dynamic = 'force-static';

const TITLE = 'About';
const DESCRIPTION =
  'Why AltStay is being built: hostels and hybrid stays run on software designed for hotels, and the gap shows up first in inventory and second on WhatsApp.';

export const metadata: Metadata = {
  title: TITLE,
  description: DESCRIPTION,
  alternates: { canonical: '/about' },
  openGraph: { title: TITLE, description: DESCRIPTION, url: '/about', type: 'website' },
  twitter: { card: 'summary_large_image', title: TITLE, description: DESCRIPTION },
};

const PLAINLY = [
  { label: 'Team', body: 'One person building it.' },
  { label: 'Built for', body: 'Hostels, homestays, surf camps, retreat centres — 20 to 120 beds or rooms.' },
  { label: 'Where', body: "India and South-East Asia first, because that's where WhatsApp is the front desk." },
  { label: 'Not building', body: 'A channel manager, a booking engine for guests, or a hotel PMS with a hostel skin.' },
] as const;

// phase-7 §5.3 — first person, short, no invented team or stock photography.
export default function AboutPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-16 sm:px-6">
      <p className="text-xs font-medium uppercase tracking-wide text-text-muted">About</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">About</h1>

      <div className="mt-6 space-y-5 text-base leading-relaxed text-text-muted">
        <p className="text-lg text-foreground">
          I&apos;m building this because the software these properties are handed was designed
          for hotels.
        </p>
        <p>
          A hotel PMS assumes a room is the unit of sale. That assumption breaks on day one for a
          hostel, a homestay, or a retreat centre — the same room might be a six-bed dorm on
          Tuesday and a private double on Saturday, and the property might also sell a seven-day
          yoga retreat and rent scooters. Owners handle the gap the only way they can: a
          spreadsheet, and their own memory.
        </p>
        <p>
          The second gap is where the guest actually is. In India and South-East Asia guests talk
          to properties on WhatsApp — before the booking and all the way through the stay. No PMS
          lives there, so the owner is the integration, personally, at 2 AM. The questions repeat,
          and the answers are already written down.
        </p>
        <p>
          So I started at that end. The concierge answers from a knowledge base the owner edits
          directly, and when a question isn&apos;t covered it hands over instead of inventing
          something. That was deliberate: a concierge that confidently makes up a pet policy is
          worse than no concierge, and it is the objection that stops the sale.
        </p>
        <p>
          The inventory model is the actual product. Bed-level units under physical rooms under
          room types, so the two ways of selling the same space can never both be available. It is
          unglamorous and it is the reason an owner would switch.
        </p>
        <p>
          Right now it is one person, running locally, with two beta properties being set up. I
          would rather show you a working demo and tell you what isn&apos;t built than put a logo
          wall on this page.
        </p>
      </div>

      <dl className="mt-10 grid gap-6 border-t border-border pt-8 sm:grid-cols-2">
        {PLAINLY.map((item) => (
          <div key={item.label}>
            <dt className="text-xs font-medium uppercase tracking-wide text-text-muted">{item.label}</dt>
            <dd className="mt-1 text-sm text-foreground">{item.body}</dd>
          </div>
        ))}
      </dl>

      <Link href="/contact" className="mt-10 inline-block text-sm font-medium text-accent hover:opacity-80">
        Talk to me →
      </Link>
    </div>
  );
}
