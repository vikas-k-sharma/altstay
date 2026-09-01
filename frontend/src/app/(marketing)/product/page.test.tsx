import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ProductPage from './page';

describe('ProductPage', () => {
  it('has three sections, one per pillar, each with a stable anchor id', () => {
    const { container } = render(<ProductPage />);

    expect(container.querySelector('#concierge')).toBeInTheDocument();
    expect(container.querySelector('#inventory')).toBeInTheDocument();
    expect(container.querySelector('#bookings')).toBeInTheDocument();
  });

  it('marks concierge as live and the other two as in build, honestly', () => {
    render(<ProductPage />);

    expect(screen.getByText(/live — try it/i)).toBeInTheDocument();
    expect(screen.getAllByText(/in build/i).length).toBeGreaterThanOrEqual(2);
  });

  it('links the concierge section to the real demo', () => {
    render(<ProductPage />);
    const links = screen.getAllByRole('link', { name: /try the concierge/i });
    links.forEach((link) => expect(link).toHaveAttribute('href', '/concierge'));
  });

  it('does not claim a screenshot of the front desk, since it is not built yet', () => {
    render(<ProductPage />);
    expect(screen.getByText(/no screenshot here yet/i)).toBeInTheDocument();
  });

  it('has no image without alt text anywhere on the page', () => {
    const { container } = render(<ProductPage />);
    container.querySelectorAll('img').forEach((img) => expect(img.getAttribute('alt')).not.toBeNull());
  });
});
