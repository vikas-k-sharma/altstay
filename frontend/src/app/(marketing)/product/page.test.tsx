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

  it('describes the inventory model for property types other than a hostel', () => {
    // 2026-09-05. Inventory was the page's most hostel-shaped section: every worked example was
    // a dorm bed, which reads as "dorms are what this supports". The schema is already general
    // (space.sale_mode is WHOLE or PER_UNIT — V7__inventory.sql), so the page must show the same
    // three layers mapped onto a camp, a homestay and a retreat centre, not just the launch
    // example. If that mapping is dropped, this fails.
    render(<ProductPage />);

    expect(screen.getByText(/the same three layers, in a property that isn't a hostel/i)).toBeInTheDocument();
    for (const type of [/surf camp/i, /homestay/i, /retreat centre/i]) {
      expect(screen.getAllByText(type).length).toBeGreaterThan(0);
    }
  });

  it('has no image without alt text anywhere on the page', () => {
    const { container } = render(<ProductPage />);
    container.querySelectorAll('img').forEach((img) => expect(img.getAttribute('alt')).not.toBeNull());
  });
});
