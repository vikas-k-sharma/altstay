import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import authUserFixture from '@/lib/contracts/__fixtures__/auth-user.json';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';
import roomTypeFixture from '@/lib/contracts/__fixtures__/room-type.json';
import spaceFixture from '@/lib/contracts/__fixtures__/space.json';
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
  useRouter: () => ({ refresh: vi.fn() }),
}));

import InventorySettingsPage from './page';

describe('InventorySettingsPage', () => {
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

  it('renders all three editors for a MANAGER', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...authUserFixture, roles: ['MANAGER'] }), { status: 200 })) // /me
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 })) // /properties
      .mockResolvedValueOnce(new Response(JSON.stringify([roomTypeFixture]), { status: 200 })) // /room-types
      .mockResolvedValueOnce(new Response(JSON.stringify([spaceFixture]), { status: 200 })); // /spaces

    const element = await InventorySettingsPage();
    render(element);

    expect(screen.getByText('Room types')).toBeInTheDocument();
    expect(screen.getByText('Spaces and units')).toBeInTheDocument();
    expect(screen.getByText(/mapping — what each room can be sold as/i)).toBeInTheDocument();
  });

  it('redirects a FRONT_DESK session to /console', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify({ ...authUserFixture, roles: ['FRONT_DESK'] }), { status: 200 })
    );

    await expect(InventorySettingsPage()).rejects.toThrow('NEXT_REDIRECT:/console');
  });
});
