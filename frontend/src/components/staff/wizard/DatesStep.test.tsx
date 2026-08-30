import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DatesStep } from './DatesStep';

describe('DatesStep', () => {
  const defaultProps = {
    today: '2026-08-30',
    initialCheckIn: '2026-08-30',
    initialCheckOut: '2026-08-31',
    initialAdults: 1,
    initialChildren: 0,
  };

  it('confirms with the entered values', () => {
    const onConfirm = vi.fn();
    render(<DatesStep {...defaultProps} onConfirm={onConfirm} />);

    fireEvent.click(screen.getByRole('button', { name: /next: room/i }));

    expect(onConfirm).toHaveBeenCalledWith('2026-08-30', '2026-08-31', 1, 0);
  });

  it('rejects a check-out on or before check-in', () => {
    const onConfirm = vi.fn();
    render(<DatesStep {...defaultProps} onConfirm={onConfirm} />);

    fireEvent.change(screen.getByLabelText('Check-out'), { target: { value: '2026-08-30' } });
    fireEvent.click(screen.getByRole('button', { name: /next: room/i }));

    expect(screen.getByRole('alert')).toHaveTextContent(/after check-in/i);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('rejects a check-in before the property\'s today', () => {
    const onConfirm = vi.fn();
    render(<DatesStep {...defaultProps} initialCheckIn="2026-08-29" onConfirm={onConfirm} />);

    fireEvent.click(screen.getByRole('button', { name: /next: room/i }));

    expect(screen.getByRole('alert')).toHaveTextContent(/before today/i);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('rejects a stay longer than the sane cap', () => {
    const onConfirm = vi.fn();
    render(<DatesStep {...defaultProps} onConfirm={onConfirm} />);

    fireEvent.change(screen.getByLabelText('Check-out'), { target: { value: '2026-10-30' } });
    fireEvent.click(screen.getByRole('button', { name: /next: room/i }));

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
