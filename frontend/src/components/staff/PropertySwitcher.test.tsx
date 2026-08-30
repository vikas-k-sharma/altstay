import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PropertySwitcher } from './PropertySwitcher';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';

const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh }),
}));

const properties = [
  propertyFixture,
  { ...propertyFixture, slug: 'driftwood-goa-annex', name: 'Driftwood Annex' },
];

describe('PropertySwitcher', () => {
  const originalFetch = global.fetch;
  const originalLocation = window.location;

  beforeEach(() => {
    refresh.mockClear();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...originalLocation, pathname: '/console', search: '', assign: vi.fn() },
    });
  });

  afterEach(() => {
    global.fetch = originalFetch;
    Object.defineProperty(window, 'location', { configurable: true, value: originalLocation });
  });

  it('posts the new slug and refreshes on selection', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 }));

    render(<PropertySwitcher properties={properties} selectedSlug="driftwood-goa" />);
    fireEvent.change(screen.getByLabelText('Switch property'), {
      target: { value: 'driftwood-goa-annex' },
    });

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(global.fetch).toHaveBeenCalledWith(
      '/api/console/property',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ slug: 'driftwood-goa-annex' }) })
    );
  });

  it('shows an error and does not refresh when the switch fails', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 404 }));

    render(<PropertySwitcher properties={properties} selectedSlug="driftwood-goa" />);
    fireEvent.change(screen.getByLabelText('Switch property'), {
      target: { value: 'driftwood-goa-annex' },
    });

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not switch/i);
    expect(refresh).not.toHaveBeenCalled();
  });

  it('redirects to login on a 401 — a dead session, not a switch failure', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 401 }));

    render(<PropertySwitcher properties={properties} selectedSlug="driftwood-goa" />);
    fireEvent.change(screen.getByLabelText('Switch property'), {
      target: { value: 'driftwood-goa-annex' },
    });

    await waitFor(() =>
      expect(window.location.assign).toHaveBeenCalledWith('/console/login?next=%2Fconsole')
    );
    expect(refresh).not.toHaveBeenCalled();
  });
});
