import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BookingTransitionActions } from './BookingTransitionActions';

const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh }),
}));

describe('BookingTransitionActions', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    refresh.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('shows only the legal transitions for BOOKED', () => {
    render(<BookingTransitionActions reference="ALT7F3K9Q" status="BOOKED" />);

    expect(screen.getByRole('button', { name: 'Checked in' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancelled' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'No-show' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Checked out' })).not.toBeInTheDocument();
  });

  it('renders nothing for a terminal status', () => {
    const { container } = render(<BookingTransitionActions reference="ALT7F3K9Q" status="CHECKED_OUT" />);
    expect(container).toBeEmptyDOMElement();
  });

  it('confirms a transition and refreshes on success', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({}), { status: 200 }));

    render(<BookingTransitionActions reference="ALT7F3K9Q" status="BOOKED" />);
    fireEvent.click(screen.getByRole('button', { name: 'Checked in' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(global.fetch).toHaveBeenCalledWith(
      '/api/console/bookings/ALT7F3K9Q/transitions',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ to: 'CHECKED_IN', reason: null }) })
    );
  });

  it('re-fetches on a 409 — the status moved under us, not a plain error', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(
        JSON.stringify({ detail: 'Cannot transition from CHECKED_OUT to CHECKED_IN' }),
        { status: 409 }
      )
    );

    render(<BookingTransitionActions reference="ALT7F3K9Q" status="BOOKED" />);
    fireEvent.click(screen.getByRole('button', { name: 'Checked in' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot transition');
    await waitFor(() => expect(refresh).toHaveBeenCalled());
  });

  it('does not refresh on a non-409 failure', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify({ detail: 'Access is denied' }), { status: 403 })
    );

    render(<BookingTransitionActions reference="ALT7F3K9Q" status="BOOKED" />);
    fireEvent.click(screen.getByRole('button', { name: 'Checked in' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Access is denied');
    expect(refresh).not.toHaveBeenCalled();
  });
});
