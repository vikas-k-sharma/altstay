import { requireSession } from '@/lib/server/session';
import { resolveActiveProperty } from '@/lib/server/property';
import { ConsoleHeader } from '@/components/staff/ConsoleHeader';
import { ConsoleNav } from '@/components/staff/ConsoleNav';

export default async function ConsoleAppLayout({ children }: { children: React.ReactNode }) {
  const session = await requireSession();
  const { properties, selected } = await resolveActiveProperty(session.cookieHeader);

  if (!selected) {
    return (
      <main className="min-h-screen flex items-center justify-center bg-zinc-100 dark:bg-zinc-950 px-4">
        <div className="max-w-md text-center space-y-2">
          <h1 className="text-lg font-bold text-zinc-900 dark:text-zinc-100">No property yet</h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            {session.user.fullName ?? session.user.email}, this workspace has no property set up
            yet. Ask an owner to create one.
          </p>
        </div>
      </main>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-zinc-100 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100">
      <ConsoleHeader user={session.user} properties={properties} selected={selected} />
      <div className="flex flex-1">
        <ConsoleNav roles={session.user.roles} />
        <main className="flex-1 p-4 sm:p-6">{children}</main>
      </div>
    </div>
  );
}
