import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusChip } from './StatusChip';

describe('StatusChip', () => {
  it('labels BOOKED', () => {
    render(<StatusChip status="BOOKED" />);
    expect(screen.getByText('Booked')).toBeInTheDocument();
  });

  it('labels CHECKED_IN', () => {
    render(<StatusChip status="CHECKED_IN" />);
    expect(screen.getByText('Checked in')).toBeInTheDocument();
  });

  it('strikes through a cancelled booking, never colour alone', () => {
    render(<StatusChip status="CANCELLED" />);
    const chip = screen.getByText('Cancelled');
    expect(chip.className).toContain('line-through');
  });

  it('labels NO_SHOW', () => {
    render(<StatusChip status="NO_SHOW" />);
    expect(screen.getByText('No-show')).toBeInTheDocument();
  });
});
