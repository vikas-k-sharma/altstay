import type { Metadata } from 'next';

export const dynamic = 'force-static';

export const metadata: Metadata = {
  title: 'Product',
  description:
    'Concierge, inventory and bookings — three parts of AltStay built on one inventory model, described plainly against what is live and what is still in build.',
};

// phase-7 §10 slice 1 — shell only. Full page (§5.2) lands in slice 3.
export default function ProductPage() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
      <h1 className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">Product</h1>
      <p className="mt-4 max-w-2xl text-lg text-text-muted">
        Three parts, in depth, lands in the next slice.
      </p>
    </div>
  );
}
