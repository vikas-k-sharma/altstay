import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import authUserFixture from '@/lib/contracts/__fixtures__/auth-user.json';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';
import versionFixture from '@/lib/contracts/__fixtures__/knowledge-base-version.json';
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

import KnowledgeBasePage from './page';

describe('KnowledgeBasePage', () => {
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

  it('fetches by property id, not slug, and pre-fills the editor', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...authUserFixture, roles: ['MANAGER'] }), { status: 200 })) // /me
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 })) // /properties
      .mockResolvedValueOnce(new Response(JSON.stringify(versionFixture), { status: 200 })) // current KB
      .mockResolvedValueOnce(new Response(JSON.stringify([versionFixture]), { status: 200 })); // history

    const element = await KnowledgeBasePage();
    render(element);

    expect(vi.mocked(global.fetch).mock.calls[2][0]).toBe(
      `http://localhost:8080/api/v1/properties/${propertyFixture.id}/knowledge-base`
    );
    expect(screen.getByLabelText('Knowledge base')).toHaveValue(versionFixture.content);
  });

  it('handles a property with no knowledge base yet (a fresh 404)', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...authUserFixture, roles: ['OWNER'] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([propertyFixture]), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 404 })) // no current version
      .mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 })); // empty history

    const element = await KnowledgeBasePage();
    render(element);

    expect(screen.getByLabelText('Knowledge base')).toHaveValue('');
  });

  it('redirects a FRONT_DESK session to /console', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify({ ...authUserFixture, roles: ['FRONT_DESK'] }), { status: 200 })
    );

    await expect(KnowledgeBasePage()).rejects.toThrow('NEXT_REDIRECT:/console');
  });
});
