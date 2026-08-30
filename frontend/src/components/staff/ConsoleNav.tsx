import Link from 'next/link';

// Presentation only — hiding an item here is not enforcement (phase-6 §9). Every write this
// hides has its own @PreAuthorize 403 on the server; this just keeps a role from being shown a
// dead end.
const NAV_ITEMS = [
  { href: '/console', label: 'Today', roles: null },
  { href: '/console/calendar', label: 'Calendar', roles: null },
  { href: '/console/bookings', label: 'Bookings', roles: null },
  { href: '/console/guests', label: 'Guests', roles: null },
  // Property settings is OWNER-only in the role matrix (phase-5 §8) — MANAGER previously saw a
  // single "Settings" link here that led to a page it would just get redirected away from.
  { href: '/console/settings/property', label: 'Property settings', roles: ['OWNER'] },
  { href: '/console/settings/inventory', label: 'Inventory', roles: ['OWNER', 'MANAGER'] },
  { href: '/console/settings/rates', label: 'Rates', roles: ['OWNER', 'MANAGER'] },
  { href: '/console/knowledge-base', label: 'Knowledge base', roles: ['OWNER', 'MANAGER'] },
] as const;

export function ConsoleNav({ roles }: { roles: readonly string[] }) {
  const items = NAV_ITEMS.filter((item) => !item.roles || item.roles.some((role) => roles.includes(role)));

  return (
    <nav aria-label="Console" className="w-48 shrink-0 border-r border-zinc-200 dark:border-zinc-800 p-3 space-y-1">
      {items.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          className="block rounded-lg px-3 py-2 text-sm text-zinc-700 dark:text-zinc-300 hover:bg-zinc-200 dark:hover:bg-zinc-800"
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
