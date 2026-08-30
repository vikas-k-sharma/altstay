import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LogoutButton } from './LogoutButton';

const push = vi.fn();
const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, refresh }),
}));

describe('LogoutButton', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    push.mockClear();
    refresh.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('calls the logout route then navigates to login', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 }));

    render(<LogoutButton />);
    fireEvent.click(screen.getByRole('button', { name: /sign out/i }));

    await waitFor(() => expect(push).toHaveBeenCalledWith('/console/login'));
    expect(global.fetch).toHaveBeenCalledWith('/api/console/logout', expect.objectContaining({ method: 'POST' }));
    expect(refresh).toHaveBeenCalled();
  });
});
