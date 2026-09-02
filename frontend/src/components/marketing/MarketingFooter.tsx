import Link from 'next/link';
import { CONTACT_EMAIL, WHATSAPP_DISPLAY } from '@/lib/marketing/contact';

export function MarketingFooter() {
  return (
    <footer className="border-t border-border bg-surface">
      <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6">
        <div className="grid grid-cols-1 gap-10 sm:grid-cols-2 lg:grid-cols-4">
          <div className="lg:col-span-1">
            <p className="text-base font-semibold text-foreground">
              AltStay <span className="text-text-muted">OS</span>
            </p>
            <p className="mt-3 max-w-xs text-sm text-text-muted">
              Property management for hostels, homestays, surf camps and retreat centres. Built
              in India, for hybrid inventory.
            </p>
          </div>

          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Product</p>
            <ul className="mt-3 space-y-2 text-sm">
              <li>
                <Link href="/product#concierge" className="text-foreground hover:text-accent">
                  Concierge
                </Link>
              </li>
              <li>
                <Link href="/product#inventory" className="text-foreground hover:text-accent">
                  Inventory
                </Link>
              </li>
              <li>
                <Link href="/product#bookings" className="text-foreground hover:text-accent">
                  Bookings
                </Link>
              </li>
            </ul>
          </div>

          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Company</p>
            <ul className="mt-3 space-y-2 text-sm">
              <li>
                <Link href="/about" className="text-foreground hover:text-accent">
                  About
                </Link>
              </li>
              <li>
                <Link href="/contact" className="text-foreground hover:text-accent">
                  Contact
                </Link>
              </li>
              <li>
                <Link href="/console/login" className="text-foreground hover:text-accent">
                  Staff login
                </Link>
              </li>
            </ul>
          </div>

          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Try it</p>
            <ul className="mt-3 space-y-2 text-sm">
              <li>
                <Link href="/concierge" className="text-accent hover:opacity-80">
                  The concierge demo →
                </Link>
              </li>
              <li>
                <a href={`mailto:${CONTACT_EMAIL}`} className="text-foreground hover:text-accent">
                  {CONTACT_EMAIL}
                </a>
              </li>
              <li className="text-text-muted">WhatsApp: {WHATSAPP_DISPLAY}</li>
            </ul>
          </div>
        </div>

        <div className="mt-10 flex flex-col gap-1 border-t border-border pt-6 text-xs text-text-muted sm:flex-row sm:items-center sm:justify-between">
          <p>© 2026 AltStay · Pre-launch</p>
          <p>No cookies set. No analytics. Nothing to consent to.</p>
        </div>
      </div>
    </footer>
  );
}
