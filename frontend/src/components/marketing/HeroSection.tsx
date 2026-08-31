import Link from 'next/link';

// phase-7 §5.1 block 1. Real arithmetic, illustrative example (roadmap §1) — not a promise about
// a reader's own savings (§3).
const BEDS = 40;
const AVERAGE_RATE = 600;
const OCCUPANCY = 0.45;
const NIGHTS_PER_YEAR = 365;
const ROOM_REVENUE = Math.round(BEDS * AVERAGE_RATE * OCCUPANCY * NIGHTS_PER_YEAR);
const COMMISSION_LOW = Math.round((ROOM_REVENUE * 0.15) / 100000);
const COMMISSION_HIGH = Math.round((ROOM_REVENUE * 0.18) / 100000);

function formatInr(amount: number) {
  return `₹${amount.toLocaleString('en-IN')}`;
}

export function HeroSection() {
  return (
    <section className="mx-auto max-w-6xl px-4 pt-16 pb-20 sm:px-6 sm:pt-20">
      <p className="text-xs font-medium uppercase tracking-[0.08em] text-text-muted">
        Property management · Hostels · Surf camps · Retreats
      </p>
      <h1 className="mt-4 max-w-4xl text-[2.5rem] font-semibold leading-[1.05] tracking-tight text-foreground sm:text-5xl lg:text-[3.5rem]">
        A hostel doing ₹40 lakh a year hands ₹6–7 lakh of it to Booking.com and Hostelworld.
      </h1>
      <p className="mt-6 max-w-2xl text-lg leading-relaxed text-text-muted">
        AltStay is a property management system for hybrid stays: an AI concierge that answers
        guests from a knowledge base you edit yourself, inventory that treats beds and rooms as
        the same thing, and a booking system that runs your front desk.
      </p>

      <div className="mt-8 flex flex-wrap items-center gap-4">
        <Link
          href="/concierge"
          className="rounded-full bg-accent px-5 py-2.5 text-sm font-medium text-white hover:opacity-90"
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

      <div className="mt-12 max-w-md rounded-2xl border border-border bg-surface p-6">
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">The arithmetic</p>
        <dl className="mt-4 space-y-3 text-sm">
          <div className="flex items-baseline justify-between">
            <dt className="text-text-muted">{BEDS} beds · {formatInr(AVERAGE_RATE)} average rate</dt>
          </div>
          <div className="flex items-baseline justify-between">
            <dt className="text-text-muted">Occupancy</dt>
            <dd className="text-foreground">{Math.round(OCCUPANCY * 100)}%</dd>
          </div>
          <div className="flex items-baseline justify-between border-t border-border pt-3">
            <dt className="text-text-muted">Room revenue, one year</dt>
            <dd className="font-semibold text-foreground">{formatInr(ROOM_REVENUE)}</dd>
          </div>
          <div className="flex items-baseline justify-between">
            <dt className="text-text-muted">OTA commission rate</dt>
            <dd className="text-foreground">15 – 18%</dd>
          </div>
          <div className="flex items-baseline justify-between border-t border-border pt-3">
            <dt className="text-text-muted">Paid out per year</dt>
            <dd className="font-semibold text-danger">
              ₹{COMMISSION_LOW}L – ₹{COMMISSION_HIGH}L
            </dd>
          </div>
        </dl>
        <p className="mt-4 text-xs text-text-muted">
          Illustrative example, not a specific property. Assumes the whole book coming through the
          OTAs — for most hostels it isn&apos;t far off. Every direct booking is the entire
          commission back.
        </p>
      </div>
    </section>
  );
}
