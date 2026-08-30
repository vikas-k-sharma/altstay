import { PropertySwitcher } from './PropertySwitcher';
import { LogoutButton } from './LogoutButton';
import type { AuthUserResponse } from '@/lib/contracts/auth';
import type { PropertyResponse } from '@/lib/contracts/property';

export function ConsoleHeader({
  user,
  properties,
  selected,
}: {
  user: AuthUserResponse;
  properties: PropertyResponse[];
  selected: PropertyResponse;
}) {
  return (
    <header className="sticky top-0 z-30 flex items-center justify-between border-b border-zinc-200 dark:border-zinc-800 bg-white/90 dark:bg-zinc-900/90 backdrop-blur px-4 sm:px-6 py-3">
      <div className="flex items-center gap-3">
        <span className="text-sm font-bold tracking-tight text-zinc-900 dark:text-zinc-100">
          AltStay Console
        </span>
        {properties.length > 1 ? (
          <PropertySwitcher properties={properties} selectedSlug={selected.slug} />
        ) : (
          <span className="text-sm text-zinc-500 dark:text-zinc-400">{selected.name}</span>
        )}
      </div>
      <div className="flex items-center gap-3 text-sm">
        {/* fullName is nullable — the provisioning runner doesn't collect one for the owner it
            creates (§12.1) — email is always present and is the honest fallback. */}
        <span className="text-zinc-600 dark:text-zinc-300">{user.fullName ?? user.email}</span>
        <LogoutButton />
      </div>
    </header>
  );
}
