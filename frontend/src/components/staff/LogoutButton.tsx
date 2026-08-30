'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';

export function LogoutButton() {
  const router = useRouter();
  const [pending, setPending] = useState(false);

  async function handleLogout() {
    if (pending) {
      return;
    }
    setPending(true);
    try {
      // Not consoleFetch: logging out already ends at /console/login, so a 401's redirect there
      // would just be a redundant hop, not a bug — either path lands the same place.
      await fetch('/api/console/logout', { method: 'POST' });
    } finally {
      router.push('/console/login');
      router.refresh();
    }
  }

  return (
    <button
      type="button"
      onClick={handleLogout}
      disabled={pending}
      className="text-sm text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100 disabled:opacity-60"
    >
      {pending ? 'Signing out…' : 'Sign out'}
    </button>
  );
}
