'use client';

import { use } from 'react';
import { LoginForm } from '@/components/staff/LoginForm';

export default function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string }>;
}) {
  const { next } = use(searchParams);

  return (
    <main className="min-h-screen flex flex-col items-center justify-center gap-8 bg-zinc-100 dark:bg-zinc-950 px-4">
      <div className="text-center">
        <h1 className="text-lg font-bold tracking-tight text-zinc-900 dark:text-zinc-100">
          AltStay Console
        </h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">Sign in to run your property.</p>
      </div>
      <LoginForm next={next} />
    </main>
  );
}
