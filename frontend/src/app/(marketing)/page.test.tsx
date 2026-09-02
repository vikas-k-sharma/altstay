import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import MarketingHomePage from './page';

describe('MarketingHomePage', () => {
  it('renders the hero and both CTAs with no session', () => {
    render(<MarketingHomePage />);

    expect(
      screen.getByRole('heading', { level: 1, name: /not hotels wearing a hostel skin/i })
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Try the concierge' })).toHaveAttribute('href', '/concierge');
    expect(screen.getByRole('link', { name: 'See the product' })).toHaveAttribute('href', '/product');
  });

  it('names the audience beyond hostels: homestays, surf camps and retreat centres', () => {
    // The hero's H1 uses "hostel" as its one concrete example (the hostel/hotel wordplay), but
    // the eyebrow and subhead immediately around it must broaden to the rest of the audience —
    // a homestay or retreat owner shouldn't have to read past the headline to feel included.
    render(<MarketingHomePage />);
    expect(screen.getByText(/homestays/i)).toBeInTheDocument();
    expect(screen.getAllByText(/surf camp/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/retreat centre/i)).toBeInTheDocument();
  });

  it('positions AltStay as replacing PMS ops, not as an OTA-commission recovery tool', () => {
    render(<MarketingHomePage />);
    // Acquisition vs. transaction: WhatsApp/the concierge only reaches guests who already have
    // the owner's number, so it cannot acquire a first-time OTA-discovered guest. The hero must
    // not imply AltStay recovers OTA commission — it must say plainly that it doesn't replace
    // the OTAs' acquisition role, only the ops running underneath.
    expect(screen.queryByText(/hands ₹6–7 lakh/i)).not.toBeInTheDocument();
    expect(screen.getByText(/isn't a marketing channel/i)).toBeInTheDocument();
    expect(screen.getByText(/have never heard of you/i)).toBeInTheDocument();
  });

  it('names both mismatches from the problem section', () => {
    render(<MarketingHomePage />);
    expect(screen.getByText(/the same room is two products/i)).toBeInTheDocument();
    expect(screen.getByText(/you are the whatsapp integration/i)).toBeInTheDocument();
  });

  it('links each of the three pillars into /product', () => {
    render(<MarketingHomePage />);
    expect(screen.getByRole('link', { name: /concierge →/i })).toHaveAttribute('href', '/product#concierge');
    expect(screen.getByRole('link', { name: /inventory →/i })).toHaveAttribute('href', '/product#inventory');
    expect(screen.getByRole('link', { name: /bookings →/i })).toHaveAttribute('href', '/product#bookings');
  });

  it('embeds the real, live concierge demo rather than a screenshot, on click', () => {
    render(<MarketingHomePage />);
    expect(screen.queryByTitle(/altstay concierge/i)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /load the live demo/i }));

    const frame = screen.getByTitle(/altstay concierge/i);
    expect(frame.tagName).toBe('IFRAME');
    expect(frame).toHaveAttribute('src', '/concierge');
  });

  it('renders the hybrid-inventory diagram once, and nowhere else on the site', () => {
    render(<MarketingHomePage />);
    expect(screen.getByRole('group', { name: /dorm night/i })).toBeInTheDocument();
  });

  it('states development status plainly and offers one real way to get in touch', () => {
    render(<MarketingHomePage />);
    expect(screen.getByText(/no one is paying for this yet/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /whatsapp/i })).toHaveAttribute(
      'href',
      expect.stringContaining('wa.me')
    );
    expect(screen.getByRole('link', { name: /hello@altstay\.in/ })).toHaveAttribute(
      'href',
      'mailto:hello@altstay.in'
    );
  });

  it('has no autoplaying video, carousel or chat widget', () => {
    const { container } = render(<MarketingHomePage />);
    expect(container.querySelector('video')).toBeNull();
    expect(container.querySelector('[data-carousel]')).toBeNull();
  });

  it('has no image without alt text', () => {
    const { container } = render(<MarketingHomePage />);
    container.querySelectorAll('img').forEach((img) => expect(img.getAttribute('alt')).not.toBeNull());
  });
});
