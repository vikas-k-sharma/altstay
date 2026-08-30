import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QuickCheckInOutButton } from './QuickCheckInOutButton';

const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh }),
}));

describe('QuickCheckInOutButton', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    refresh.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('renders nothing when the target is not a legal transition (a NO_SHOW arrival)', () => {
    const { container } = render(
      <QuickCheckInOutButton reference="ALT1" status="NO_SHOW" target="CHECKED_IN" />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('checks in and refreshes immediately when the check-in is not early', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ earlyCheckIn: false }), { status: 200 }));

    render(<QuickCheckInOutButton reference="ALT1" status="BOOKED" target="CHECKED_IN" />);
    fireEvent.click(screen.getByRole('button', { name: 'Check in' }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(screen.queryByText(/early check-in/i)).not.toBeInTheDocument();
  });

  it('notes an early check-in and delays the refresh so the note is visible', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ earlyCheckIn: true }), { status: 200 }));

    render(<QuickCheckInOutButton reference="ALT1" status="BOOKED" target="CHECKED_IN" />);
    fireEvent.click(screen.getByRole('button', { name: 'Check in' }));

    expect(await screen.findByText('Early check-in noted')).toBeInTheDocument();
    expect(refresh).not.toHaveBeenCalled();

    // Real timers: the component's own 1.5s delay is what's under test here.
    await waitFor(() => expect(refresh).toHaveBeenCalled(), { timeout: 2500 });
  }, 3000);

  it('shows an error and does not refresh on a non-409 failure', async () => {
    global.fetch = vi
      .fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ detail: 'Access is denied' }), { status: 403 }));

    render(<QuickCheckInOutButton reference="ALT1" status="BOOKED" target="CHECKED_IN" />);
    fireEvent.click(screen.getByRole('button', { name: 'Check in' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Access is denied');
    expect(refresh).not.toHaveBeenCalled();
  });

  it('renders Check out only when CHECKED_IN can legally move there', () => {
    render(<QuickCheckInOutButton reference="ALT1" status="CHECKED_IN" target="CHECKED_OUT" />);
    expect(screen.getByRole('button', { name: 'Check out' })).toBeInTheDocument();
  });
});
