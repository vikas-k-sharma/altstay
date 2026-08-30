import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { GuestFilterPicker } from './GuestFilterPicker';

const push = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push }),
  usePathname: () => '/console/bookings',
  useSearchParams: () => new URLSearchParams('status=BOOKED'),
}));

const guests = [
  { id: 'g1', fullName: 'Arjun Mehta', email: null, phone: null, countryCode: null, dateOfBirth: null, notes: null },
  { id: 'g2', fullName: 'Priya Nair', email: null, phone: null, countryCode: null, dateOfBirth: null, notes: null },
];

describe('GuestFilterPicker', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    push.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('shows a static summary and a Clear link when a guest is already selected', () => {
    render(<GuestFilterPicker selectedGuestName="Arjun Mehta" />);

    expect(screen.getByText('Arjun Mehta')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Clear' }));
    expect(push).toHaveBeenCalledWith('/console/bookings?status=BOOKED');
  });

  it('does not search until two characters are typed', () => {
    global.fetch = vi.fn();
    render(<GuestFilterPicker />);

    fireEvent.change(screen.getByLabelText('Find a guest'), { target: { value: 'A' } });

    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('shows matches and navigates with the picked guest id, preserving other filters', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(guests), { status: 200 }));
    render(<GuestFilterPicker />);

    fireEvent.change(screen.getByLabelText('Find a guest'), { target: { value: 'ar' } });

    fireEvent.click(await screen.findByRole('button', { name: 'Arjun Mehta' }));

    await waitFor(() =>
      expect(push).toHaveBeenCalledWith('/console/bookings?status=BOOKED&guestId=g1')
    );
  });
});
