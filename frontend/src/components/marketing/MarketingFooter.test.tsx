import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MarketingFooter } from './MarketingFooter';

describe('MarketingFooter', () => {
  it('links every product pillar, company page and the demo, plus a real mailto', () => {
    render(<MarketingFooter />);

    expect(screen.getByRole('link', { name: 'Concierge' })).toHaveAttribute('href', '/product#concierge');
    expect(screen.getByRole('link', { name: 'Inventory' })).toHaveAttribute('href', '/product#inventory');
    expect(screen.getByRole('link', { name: 'Bookings' })).toHaveAttribute('href', '/product#bookings');
    expect(screen.getByRole('link', { name: 'About' })).toHaveAttribute('href', '/about');
    expect(screen.getByRole('link', { name: 'Contact' })).toHaveAttribute('href', '/contact');
    expect(screen.getByRole('link', { name: 'Staff login' })).toHaveAttribute('href', '/console/login');
    expect(screen.getByRole('link', { name: /concierge demo/i })).toHaveAttribute('href', '/concierge');
    expect(screen.getByRole('link', { name: /hello@altstay\.in/ })).toHaveAttribute(
      'href',
      'mailto:hello@altstay.in'
    );
  });

  it('states plainly that nothing is tracked', () => {
    render(<MarketingFooter />);
    expect(screen.getByText(/no cookies set/i)).toBeInTheDocument();
  });

  it('has no image without alt text', () => {
    const { container } = render(<MarketingFooter />);
    const images = container.querySelectorAll('img');
    images.forEach((img) => expect(img.getAttribute('alt')).not.toBeNull());
  });
});
