import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ContactPage from './page';

describe('ContactPage', () => {
  it('renders no form — two real ways to reach a person instead', () => {
    const { container } = render(<ContactPage />);

    expect(container.querySelector('form')).toBeNull();
    expect(screen.getByRole('link', { name: /open whatsapp/i })).toHaveAttribute(
      'href',
      expect.stringContaining('wa.me')
    );
    expect(screen.getByRole('link', { name: /write an email/i })).toHaveAttribute(
      'href',
      'mailto:hello@altstay.in'
    );
  });

  it('states a response-time expectation', () => {
    render(<ContactPage />);
    expect(screen.getByText(/9 am to 9 pm ist/i)).toBeInTheDocument();
  });

  it('has no image without alt text', () => {
    const { container } = render(<ContactPage />);
    container.querySelectorAll('img').forEach((img) => expect(img.getAttribute('alt')).not.toBeNull());
  });
});
