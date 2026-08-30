import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { LoginForm } from './LoginForm';

const push = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push }),
}));

function fillAndSubmit() {
  fireEvent.change(screen.getByLabelText('Workspace'), { target: { value: 'driftwood' } });
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'owner@driftwood.example' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'hunter2' } });
  fireEvent.click(screen.getByRole('button', { name: /sign in/i }));
}

describe('LoginForm', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    push.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('redirects to the validated next path on success', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({}), { status: 200 }));
    render(<LoginForm next="/console/bookings" />);

    fillAndSubmit();

    await waitFor(() => expect(push).toHaveBeenCalledWith('/console/bookings'));
  });

  it('ignores a next path outside /console/ to avoid an open redirect', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({}), { status: 200 }));
    render(<LoginForm next="https://evil.example/phish" />);

    fillAndSubmit();

    await waitFor(() => expect(push).toHaveBeenCalledWith('/console'));
  });

  it('shows the single backend refusal message and does not double-submit', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          type: 'https://api.altstay.com/errors/unauthorized',
          title: 'Unauthorized',
          status: 401,
          detail: 'Invalid credentials',
        }),
        { status: 401 }
      )
    );
    render(<LoginForm />);

    fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid credentials');
    expect(push).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /sign in/i })).not.toBeDisabled();
  });

  it('disables the submit button while a request is in flight', async () => {
    let resolveFetch: (value: Response) => void = () => {};
    global.fetch = vi.fn().mockReturnValueOnce(
      new Promise((resolve) => {
        resolveFetch = resolve;
      })
    );
    render(<LoginForm />);

    fillAndSubmit();

    expect(screen.getByRole('button', { name: /signing in/i })).toBeDisabled();
    resolveFetch(new Response(JSON.stringify({}), { status: 200 }));
    await waitFor(() => expect(push).toHaveBeenCalled());
  });

  it('shows a network-error message when the request throws', async () => {
    global.fetch = vi.fn().mockRejectedValueOnce(new Error('network down'));
    render(<LoginForm />);

    fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach/i);
  });
});
