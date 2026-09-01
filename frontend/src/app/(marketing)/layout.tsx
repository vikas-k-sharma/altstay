import type { Metadata } from 'next';
import { MarketingNav } from '@/components/marketing/MarketingNav';
import { MarketingFooter } from '@/components/marketing/MarketingFooter';
import { SITE_URL } from '@/lib/marketing/site';

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: 'AltStay — Property Management for Hybrid Stays',
    template: '%s · AltStay',
  },
};

// phase-7 §4.1 — escape hatch taken deliberately: rather than moving the demo's body classes
// out of the root layout (its own isolated, re-walked step), this layout overrides what it
// needs on its own container and leaves src/app/layout.tsx untouched. Cheaper, and it makes the
// "the demo is unaffected" guarantee trivially true rather than something to re-verify.
export default function MarketingLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-full flex-col bg-surface font-[var(--font-geist-sans)] text-foreground">
      <MarketingNav />
      <main className="flex-1">{children}</main>
      <MarketingFooter />
    </div>
  );
}
