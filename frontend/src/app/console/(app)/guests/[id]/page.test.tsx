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
  notFound: vi.fn(() => {
    throw new Error('NEXT_NOT_FOUND');
  }),
  useRouter: () => ({ refresh: vi.fn() }),
}));

import GuestDetailPage from './page';

const guestFixture = {
  id: 'c7f72790-b9d3-405e-abbc-748a7ed7ccf9',
  fullName: 'Arjun Mehta',
  email: 'arjun@example.com',
  phone: null,
  countryCode: 'IN',
  dateOfBirth: null,
  notes: null,
};

describe('GuestDetailPage', () => {
  const originalFetch = global.fetch;
  const originalBackendUrl = process.env.BACKEND_URL;

  beforeEach(() => {
    cookieStore.clear();
    cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
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

  it('renders the edit form pre-filled with the guest', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 })) // /me
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 })) // /properties
      .mockResolvedValueOnce(new Response(JSON.stringify(guestFixture), { status: 200 })); // /guests/:id

    const element = await GuestDetailPage({ params: Promise.resolve({ id: guestFixture.id }) });
    render(element);

    expect(screen.getByRole('heading', { name: 'Arjun Mehta' })).toBeInTheDocument();
    expect(screen.getByLabelText('Full name')).toHaveValue('Arjun Mehta');
    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument();
  });

  it('calls notFound() for an unknown guest id', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authUserFixture), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 404 }));

    await expect(
      GuestDetailPage({ params: Promise.resolve({ id: '00000000-0000-4000-8000-000000000000' }) })
    ).rejects.toThrow('NEXT_NOT_FOUND');
  });
});
