import { describe, it, expect, vi } from 'vitest';
import { redirect } from 'next/navigation';
import HomePage from './page';

vi.mock('next/navigation', () => ({
  redirect: vi.fn(),
}));

describe('HomePage', () => {
  it('redirects to /concierge, the demo\'s new address', () => {
    HomePage();

    expect(redirect).toHaveBeenCalledTimes(1);
    expect(redirect).toHaveBeenCalledWith('/concierge');
  });
});
