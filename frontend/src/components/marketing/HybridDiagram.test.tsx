import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { HybridDiagram } from './HybridDiagram';

// phase-7 §5.1 block 5 — roadmap §5's crux, shown not asserted: selling a bed closes the whole
// room, and vice versa. This is the one diagram the plan permits, and it must show real states,
// not decoration, so the test pins the actual bed-by-bed data rather than just "it renders".
describe('HybridDiagram', () => {
  it('shows four beds sold and two free on the dorm night, with the private room unavailable', () => {
    render(<HybridDiagram />);

    const dormPanel = screen.getByRole('group', { name: /dorm night/i });
    expect(within(dormPanel).getAllByText('sold')).toHaveLength(4);
    expect(within(dormPanel).getAllByText('free')).toHaveLength(2);
    expect(within(dormPanel).getByText(/room can't go whole/i)).toBeInTheDocument();
  });

  it('shows all six beds held on the private night, with the whole room sold', () => {
    render(<HybridDiagram />);

    const privatePanel = screen.getByRole('group', { name: /private night/i });
    expect(within(privatePanel).getAllByText('held')).toHaveLength(6);
    expect(within(privatePanel).getByText(/six beds off the market/i)).toBeInTheDocument();
  });
});
