import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { GuestForm } from './GuestForm';

const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh }),
}));

const guest = {
  id: 'g1',
  fullName: 'Arjun Mehta',
  email: 'arjun@example.com',
  phone: null,
  countryCode: 'IN',
  dateOfBirth: null,
  notes: null,
};

describe('GuestForm', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    refresh.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('creates a guest with POST and clears the form on success', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(guest), { status: 201 }));

    render(<GuestForm />);
    fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'New Guest' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add guest' }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(global.fetch).toHaveBeenCalledWith(
      '/api/console/guests',
      expect.objectContaining({ method: 'POST' })
    );
    expect(screen.getByLabelText('Full name')).toHaveValue('');
  });

  it('updates an existing guest with PUT and keeps the fields filled in', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(guest), { status: 200 }));

    render(<GuestForm guest={guest} />);
    fireEvent.change(screen.getByLabelText('Phone'), { target: { value: '+91 9000000000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(global.fetch).toHaveBeenCalledWith('/api/console/guests/g1', expect.objectContaining({ method: 'PUT' }));
    expect(screen.getByLabelText('Full name')).toHaveValue('Arjun Mehta');
  });

  it('shows the backend refusal and does not refresh on failure', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify({ detail: 'fullName is required' }), { status: 400 })
    );

    render(<GuestForm />);
    fireEvent.click(screen.getByRole('button', { name: 'Add guest' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('fullName is required');
    expect(refresh).not.toHaveBeenCalled();
  });
});
