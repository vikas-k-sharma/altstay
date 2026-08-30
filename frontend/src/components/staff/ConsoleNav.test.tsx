import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ConsoleNav } from './ConsoleNav';

describe('ConsoleNav', () => {
  it('hides every settings-adjacent link from a FRONT_DESK role', () => {
    render(<ConsoleNav roles={['FRONT_DESK']} />);

    expect(screen.getByRole('link', { name: 'Today' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Bookings' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Property settings' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Inventory' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Rates' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Knowledge base' })).not.toBeInTheDocument();
  });

  it('shows property settings only to an OWNER, matching the role matrix exactly', () => {
    render(<ConsoleNav roles={['OWNER']} />);
    expect(screen.getByRole('link', { name: 'Property settings' })).toBeInTheDocument();
  });

  it('shows inventory, rates and knowledge base to a MANAGER, but not property settings', () => {
    render(<ConsoleNav roles={['MANAGER']} />);

    expect(screen.getByRole('link', { name: 'Inventory' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Rates' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Knowledge base' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Property settings' })).not.toBeInTheDocument();
  });
});
