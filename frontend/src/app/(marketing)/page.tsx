import type { Metadata } from 'next';
import { HeroSection } from '@/components/marketing/HeroSection';
import { ProblemSection } from '@/components/marketing/ProblemSection';
import { PillarsSection } from '@/components/marketing/PillarsSection';
import { DemoSection } from '@/components/marketing/DemoSection';
import { HybridExplainerSection } from '@/components/marketing/HybridExplainerSection';
import { StatusCtaSection } from '@/components/marketing/StatusCtaSection';

export const dynamic = 'force-static';

const TITLE = 'AltStay — Property Management for Hybrid Stays';
const DESCRIPTION =
  'AltStay is a property management system for hostels, homestays, surf camps and retreat centres: an AI concierge, hybrid inventory, and a booking system that runs your front desk.';

export const metadata: Metadata = {
  title: TITLE,
  description: DESCRIPTION,
  alternates: { canonical: '/' },
  openGraph: { title: TITLE, description: DESCRIPTION, url: '/', type: 'website' },
  twitter: { card: 'summary_large_image', title: TITLE, description: DESCRIPTION },
};

// phase-7 §5.1 — six blocks, each its own component with its copy in one place, so a KILL
// verdict on the R0 gate (§0) reorders the pillars block without touching a component.
export default function MarketingHomePage() {
  return (
    <>
      <HeroSection />
      <ProblemSection />
      <PillarsSection />
      <DemoSection />
      <HybridExplainerSection />
      <StatusCtaSection />
    </>
  );
}
