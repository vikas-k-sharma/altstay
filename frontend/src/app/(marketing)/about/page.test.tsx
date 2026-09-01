import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import AboutPage from './page';

describe('AboutPage', () => {
  it('renders one heading and no invented team', () => {
    render(<AboutPage />);

    expect(screen.getByRole('heading', { level: 1, name: 'About' })).toBeInTheDocument();
    expect(screen.getByText(/one person building it/i)).toBeInTheDocument();
    expect(screen.queryByText(/our team/i)).not.toBeInTheDocument();
  });

  it('links onward to a way to get in touch', () => {
    render(<AboutPage />);
    expect(screen.getByRole('link', { name: /talk to me/i })).toHaveAttribute('href', '/contact');
  });

  it('has no image without alt text', () => {
    const { container } = render(<AboutPage />);
    container.querySelectorAll('img').forEach((img) => expect(img.getAttribute('alt')).not.toBeNull());
  });
});
