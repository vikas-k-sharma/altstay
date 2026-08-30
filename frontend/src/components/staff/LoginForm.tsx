'use client';

import { useState, FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import { ProblemDetailSchema } from '@/lib/contracts/problem';

type Status = 'idle' | 'submitting' | 'refused' | 'network-error';

function resolveNext(next: string | undefined): string {
  return next && next.startsWith('/console/') ? next : '/console';
}

export function LoginForm({ next }: { next?: string }) {
  const router = useRouter();
  const [tenantSlug, setTenantSlug] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [status, setStatus] = useState<Status>('idle');
  const [message, setMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (status === 'submitting') {
      return;
    }
    setStatus('submitting');
    setMessage(null);

    try {
      const response = await fetch('/api/console/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ tenantSlug, email, password }),
      });

      if (response.ok) {
        router.push(resolveNext(next));
        return;
      }

      const body = await response.json().catch(() => undefined);
      const parsed = ProblemDetailSchema.safeParse(body);
      setStatus('refused');
      setMessage(parsed.success ? parsed.data.detail : 'Sign-in failed. Please try again.');
    } catch {
      setStatus('network-error');
      setMessage('Could not reach AltStay. Check your connection and try again.');
    }
  }

  const submitting = status === 'submitting';

  return (
    <form onSubmit={handleSubmit} className="w-full max-w-sm space-y-4" noValidate>
      <div>
        <label htmlFor="tenantSlug" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
          Workspace
        </label>
        <input
          id="tenantSlug"
          name="tenantSlug"
          type="text"
          autoComplete="organization"
          required
          value={tenantSlug}
          onChange={(e) => setTenantSlug(e.target.value)}
          className="mt-1 w-full rounded-lg border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 px-3 py-2 text-sm text-zinc-900 dark:text-zinc-100"
        />
        <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">
          Your workspace slug — the same email can hold accounts at more than one property, so we
          need to know which one you mean.
        </p>
      </div>

      <div>
        <label htmlFor="email" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
          Email
        </label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="username"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="mt-1 w-full rounded-lg border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 px-3 py-2 text-sm text-zinc-900 dark:text-zinc-100"
        />
      </div>

      <div>
        <label htmlFor="password" className="block text-sm font-medium text-zinc-700 dark:text-zinc-300">
          Password
        </label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="mt-1 w-full rounded-lg border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 px-3 py-2 text-sm text-zinc-900 dark:text-zinc-100"
        />
      </div>

      {message && (
        <p role="alert" className="text-sm text-red-600 dark:text-red-400">
          {message}
        </p>
      )}

      <button
        type="submit"
        disabled={submitting}
        aria-disabled={submitting}
        aria-busy={submitting}
        className="w-full rounded-lg bg-emerald-600 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
      >
        {submitting ? 'Signing in…' : 'Sign in'}
      </button>
    </form>
  );
}
