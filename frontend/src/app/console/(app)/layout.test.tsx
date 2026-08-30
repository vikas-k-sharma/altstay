import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import authUserFixture from '@/lib/contracts/__fixtures__/auth-user.json';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';
import { SESSION_COOKIE_NAME } from '@/lib/server/session';

const cookieStore = new Map<string, { value: string }>();

vi.mock('next/headers', () => ({
  cookies: vi.fn(async () => ({
    get: (name: string) => cookieStore.get(name),
  })),
}));

vi.mock('next/navigation', () => ({
  redirect: vi.fn((path: string) => {
    throw new Error(`NEXT_REDIRECT:${path}`);
  }),
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

import { redirect } from 'next/navigation';
import ConsoleAppLayout from './layout';

describe('ConsoleAppLayout', () => {
  const originalFetch = global.fetch;
  const originalBackendUrl = process.env.BACKEND_URL;

  beforeEach(() => {
    vi.clearAllMocks();
    cookieStore.clear();
    process.env.BACKEND_URL = 'http://localhost:8080';
  });

  afterEach(() => {
    global.fetch = originalFetch;
    if (originalBackendUrl !== undefined) {
      process.env.BACKEND_URL = originalBackendUrl;
    } else {
      delete process.env.BACKEND_URL;
    }
  });

  it('redirects to login when there is no session', async () => {
    await expect(ConsoleAppLayout({ children: <div /> })).rejects.toThrow('NEXT_REDIRECT:/console/login');
    expect(redirect).toHaveBeenCalledWith('/console/login');
  });

  it('renders the shell with the header, nav and the resolved property once signed in', async () => {
    cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 })) // /me
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 })); // /properties

    const element = await ConsoleAppLayout({ children: <div data-testid="child">Today</div> });
    render(element);

    expect(screen.getByText('Driftwood Beach Hostel')).toBeInTheDocument();
    expect(screen.getByText('Priya Nair')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Today' })).toBeInTheDocument();
    expect(screen.getByTestId('child')).toBeInTheDocument();
  });

  it('renders an onboarding message instead of the shell when the tenant has no property', async () => {
    cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 })) // /me
      .mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 })); // /properties

    const element = await ConsoleAppLayout({ children: <div /> });
    render(element);

    expect(screen.getByText(/no property yet/i)).toBeInTheDocument();
  });
});
