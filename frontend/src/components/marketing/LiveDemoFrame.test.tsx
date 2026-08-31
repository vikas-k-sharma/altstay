import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { LiveDemoFrame } from './LiveDemoFrame';

// The concierge composer autofocuses on mount (by design — it's a chat UI). Embedding it in an
// eager iframe makes the *marketing page* jump-scroll to wherever the iframe sits the instant it
// loads, because the browser scrolls a newly focused element into view. Click-to-load defers
// that focus until the user has already clicked inside the frame's own position.
describe('LiveDemoFrame', () => {
  it('does not load the iframe until the user asks for it', () => {
    render(<LiveDemoFrame />);
    expect(screen.queryByTitle(/altstay concierge/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /load the live demo/i })).toBeInTheDocument();
  });

  it('loads the real /concierge route on click, and only then', () => {
    render(<LiveDemoFrame />);
    fireEvent.click(screen.getByRole('button', { name: /load the live demo/i }));

    const frame = screen.getByTitle(/altstay concierge/i);
    expect(frame.tagName).toBe('IFRAME');
    expect(frame).toHaveAttribute('src', '/concierge');
  });
});
