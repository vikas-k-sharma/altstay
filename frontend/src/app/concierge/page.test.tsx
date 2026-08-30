import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ConciergePage from './page';

describe('ConciergePage', () => {
  it('renders the concierge demo with no session, auth check, or redirect', () => {
    render(<ConciergePage />);

    expect(screen.getByRole('heading', { name: 'AltStay' })).toBeInTheDocument();
    expect(screen.getAllByLabelText('Guest WhatsApp Chat').length).toBeGreaterThan(0);
    expect(screen.getAllByLabelText('Hostel Rules and Knowledge Base').length).toBeGreaterThan(0);
  });
});
