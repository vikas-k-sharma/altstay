import type { Metadata } from 'next';
import Link from 'next/link';
import { ConciergeDetailSection } from '@/components/marketing/product/ConciergeDetailSection';
import { InventoryDetailSection } from '@/components/marketing/product/InventoryDetailSection';
import { BookingsDetailSection } from '@/components/marketing/product/BookingsDetailSection';

export const dynamic = 'force-static';

const TITLE = 'Product';
const DESCRIPTION =
  'Concierge, inventory and bookings — three parts of AltStay on one inventory model that fits a hostel, a homestay, a camp or a retreat, described plainly against what is live and what is still in build.';

export const metadata: Metadata = {
  title: TITLE,
  description: DESCRIPTION,
  alternates: { canonical: '/product' },
  openGraph: { title: TITLE, description: DESCRIPTION, url: '/product', type: 'website' },
  twitter: { card: 'summary_large_image', title: TITLE, description: DESCRIPTION },
};

export default function ProductPage() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
      <p className="text-xs font-medium uppercase tracking-wide text-text-muted">The product</p>
      <h1 className="mt-3 max-w-2xl text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
        Three parts. One inventory model underneath all of them.
      </h1>
      <p className="mt-4 max-w-2xl text-lg leading-relaxed text-text-muted">
        The concierge is what you can use today. Inventory is what makes the rest of it correct —
        one model that covers a dorm bed, a tent bunk, a private room and a whole cottage.
        Bookings is the front desk your staff will actually spend the day in. Where something
        isn&apos;t built yet, this page says so.
      </p>

      <ConciergeDetailSection />
      <InventoryDetailSection />
      <BookingsDetailSection />

      <div className="border-t border-border py-16">
        <p className="max-w-xl text-lg font-medium text-foreground">
          The concierge is the part you can judge today.
        </p>
        <p className="mt-2 max-w-xl text-sm leading-relaxed text-text-muted">
          Everything above sits on the same inventory model. If the concierge convinces you, the
          rest is worth a conversation.
        </p>
        <div className="mt-6 flex flex-wrap gap-4">
          <Link
            href="/concierge"
            className="rounded-full bg-accent px-5 py-2.5 text-sm font-medium text-accent-foreground hover:opacity-90"
          >
            Try the concierge
          </Link>
          <Link
            href="/contact"
            className="rounded-full border border-border px-5 py-2.5 text-sm font-medium text-foreground hover:bg-surface-muted"
          >
            Get in touch
          </Link>
        </div>
      </div>
    </div>
  );
}
