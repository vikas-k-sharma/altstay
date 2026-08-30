import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import authUserFixture from '@/lib/contracts/__fixtures__/auth-user.json';

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
}));

import { redirect } from 'next/navigation';
import {
  upstream,
  getSession,
  requireSession,
  requireRole,
  SESSION_COOKIE_NAME,
} from './session';

describe('session.ts', () => {
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

  describe('upstream', () => {
    it('throws when BACKEND_URL is unset', async () => {
      delete process.env.BACKEND_URL;
      await expect(upstream('/api/v1/auth/me')).rejects.toThrow('BACKEND_URL is not set');
    });

    it('calls the backend origin and attaches the Cookie header', async () => {
      global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 }));

      await upstream('/api/v1/auth/logout', { method: 'POST', cookieHeader: 'JSESSIONID=abc123' });

      expect(global.fetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/v1/auth/logout',
        expect.objectContaining({ method: 'POST' })
      );
      const [, init] = vi.mocked(global.fetch).mock.calls[0];
      const headers = init?.headers as Headers;
      expect(headers.get('Cookie')).toBe('JSESSIONID=abc123');
    });
  });

  describe('getSession', () => {
    it('returns null when there is no session cookie', async () => {
      const session = await getSession();
      expect(session).toBeNull();
    });

    it('returns null when the upstream session is dead', async () => {
      cookieStore.set(SESSION_COOKIE_NAME, { value: 'expired' });
      global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 401 }));

      const session = await getSession();
      expect(session).toBeNull();
    });

    it('returns null when the upstream body fails to parse against AuthUserResponse', async () => {
      cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
      global.fetch = vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify({ nonsense: true }), { status: 200 })
      );

      const session = await getSession();
      expect(session).toBeNull();
    });

    it('returns the session with its cookie header on success', async () => {
      cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
      global.fetch = vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify(authUserFixture), { status: 200 })
      );

      const session = await getSession();
      expect(session).not.toBeNull();
      expect(session?.cookieHeader).toBe('JSESSIONID=abc123');
      expect(session?.user.tenantSlug).toBe('driftwood');
    });
  });

  describe('requireSession', () => {
    it('returns the session when present', async () => {
      cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
      global.fetch = vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify(authUserFixture), { status: 200 })
      );

      const session = await requireSession('/console/bookings');
      expect(session.user.tenantSlug).toBe('driftwood');
      expect(redirect).not.toHaveBeenCalled();
    });

    it('redirects to /console/login when absent', async () => {
      await expect(requireSession()).rejects.toThrow('NEXT_REDIRECT:/console/login');
    });

    it('preserves the current path as ?next= when absent', async () => {
      await expect(requireSession('/console/bookings')).rejects.toThrow(
        'NEXT_REDIRECT:/console/login?next=%2Fconsole%2Fbookings'
      );
    });
  });

  describe('requireRole', () => {
    it('returns the session when the role is present', async () => {
      cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
      global.fetch = vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify(authUserFixture), { status: 200 })
      );

      const session = await requireRole(['OWNER']);
      expect(session.user.roles).toContain('OWNER');
    });

    it('redirects to /console when the role is missing', async () => {
      cookieStore.set(SESSION_COOKIE_NAME, { value: 'abc123' });
      global.fetch = vi.fn().mockResolvedValueOnce(
        new Response(JSON.stringify(authUserFixture), { status: 200 })
      );

      await expect(requireRole(['MANAGER'])).rejects.toThrow('NEXT_REDIRECT:/console');
    });
  });
});
