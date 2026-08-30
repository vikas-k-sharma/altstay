import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { GuestStep } from './GuestStep';

const guests = [
  { id: 'g1', fullName: 'Arjun Mehta', email: 'arjun@example.com', phone: null, countryCode: null, dateOfBirth: null, notes: null },
];

describe('GuestStep', () => {
  const originalFetch = global.fetch;

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('picks an existing guest from search', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(guests), { status: 200 }));
    const onConfirm = vi.fn();
    render(<GuestStep onConfirm={onConfirm} onBack={vi.fn()} />);

    fireEvent.change(screen.getByLabelText('Search guest name'), { target: { value: 'ar' } });
    fireEvent.click(await screen.findByRole('button', { name: 'Arjun Mehta' }));
    fireEvent.click(screen.getByRole('button', { name: /next: review/i }));

    expect(onConfirm).toHaveBeenCalledWith(guests[0]);
  });

  it('refuses to advance the search mode with nothing picked', () => {
    const onConfirm = vi.fn();
    render(<GuestStep onConfirm={onConfirm} onBack={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: /next: review/i }));

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('requires a full name for a new guest', () => {
    const onConfirm = vi.fn();
    render(<GuestStep onConfirm={onConfirm} onBack={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'New guest' }));
    fireEvent.click(screen.getByRole('button', { name: /next: review/i }));

    expect(screen.getByRole('alert')).toHaveTextContent(/full name/i);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('requires an email or phone for a new guest', () => {
    const onConfirm = vi.fn();
    render(<GuestStep onConfirm={onConfirm} onBack={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'New guest' }));
    fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'New Guest' } });
    fireEvent.click(screen.getByRole('button', { name: /next: review/i }));

    expect(screen.getByRole('alert')).toHaveTextContent(/email or phone/i);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('confirms a new guest with the entered fields', () => {
    const onConfirm = vi.fn();
    render(<GuestStep onConfirm={onConfirm} onBack={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: 'New guest' }));
    fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'New Guest' } });
    fireEvent.change(screen.getByLabelText('Phone'), { target: { value: '+91 9000000000' } });
    fireEvent.click(screen.getByRole('button', { name: /next: review/i }));

    expect(onConfirm).toHaveBeenCalledWith({
      id: null,
      fullName: 'New Guest',
      email: null,
      phone: '+91 9000000000',
      countryCode: null,
      dateOfBirth: null,
      notes: null,
    });
  });
});
