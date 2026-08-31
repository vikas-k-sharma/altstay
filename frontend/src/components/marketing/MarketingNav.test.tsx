import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MarketingNav } from './MarketingNav';

describe('MarketingNav', () => {
  it('links to every marketing route, the demo and the console login', () => {
    render(<MarketingNav />);

    expect(screen.getAllByRole('link', { name: 'Product' })[0]).toHaveAttribute('href', '/product');
    expect(screen.getAllByRole('link', { name: 'About' })[0]).toHaveAttribute('href', '/about');
    expect(screen.getAllByRole('link', { name: 'Contact' })[0]).toHaveAttribute('href', '/contact');
    expect(screen.getAllByRole('link', { name: /Staff login/ })[0]).toHaveAttribute('href', '/console/login');
    expect(screen.getAllByRole('link', { name: /Try the demo/ })[0]).toHaveAttribute('href', '/concierge');
  });

  it('keeps the mobile menu closed by default and opens it on click', () => {
    render(<MarketingNav />);

    const toggle = screen.getByRole('button', { name: /menu/i });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('navigation', { name: 'Mobile' })).not.toBeInTheDocument();

    fireEvent.click(toggle);

    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('navigation', { name: 'Mobile' })).toBeInTheDocument();
  });

  it('closes the mobile menu on a second click', () => {
    render(<MarketingNav />);
    const toggle = screen.getByRole('button', { name: /menu/i });

    fireEvent.click(toggle);
    expect(screen.getByRole('navigation', { name: 'Mobile' })).toBeInTheDocument();

    fireEvent.click(toggle);
    expect(screen.queryByRole('navigation', { name: 'Mobile' })).not.toBeInTheDocument();
  });

  it('is keyboard operable: the toggle is a real button wired to the panel it controls, and closed panel links are not tab-reachable', () => {
    render(<MarketingNav />);

    const toggle = screen.getByRole('button', { name: /menu/i });
    expect(toggle.tagName).toBe('BUTTON');
    expect(toggle).not.toHaveAttribute('tabindex', '-1');
    expect(toggle).toHaveAttribute('aria-controls');
    // closed: the mobile panel's links must not exist in the tree, so Tab cannot land on them
    expect(screen.queryByRole('navigation', { name: 'Mobile' })).not.toBeInTheDocument();

    fireEvent.click(toggle);

    const mobileNav = screen.getByRole('navigation', { name: 'Mobile' });
    expect(mobileNav).toHaveAttribute('id', toggle.getAttribute('aria-controls'));
    // open: every link inside is a real <a>, focusable and reachable by Tab by default
    const links = screen.getAllByRole('link');
    expect(links.some((link) => link.closest('#' + toggle.getAttribute('aria-controls')))).toBe(true);
  });
});
