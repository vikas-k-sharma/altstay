import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { RoomStep } from './RoomStep';
import availabilityFixture from '@/lib/contracts/__fixtures__/availability.json';
import type { PropertyAvailabilityResponse } from '@/lib/contracts/availability';

const availability = availabilityFixture as PropertyAvailabilityResponse;

describe('RoomStep', () => {
  it('shows a loading state before availability arrives', () => {
    render(<RoomStep availability={null} initialRoomTypeId={null} onConfirm={vi.fn()} onBack={vi.fn()} />);
    expect(screen.getByText(/loading availability/i)).toBeInTheDocument();
  });

  it('confirms a PER_UNIT room type with the chosen bed count', () => {
    const onConfirm = vi.fn();
    render(<RoomStep availability={availability} initialRoomTypeId={null} onConfirm={onConfirm} onBack={vi.fn()} />);

    fireEvent.click(screen.getByLabelText(/MIXED-6/));
    fireEvent.change(screen.getByLabelText('Beds'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: /next: guest/i }));

    expect(onConfirm).toHaveBeenCalledWith(availability.roomTypes[0].roomTypeId, 3);
  });

  it("uses bookableWholeSpaces (range-wide) for a WHOLE type, never the per-day count, and always sends unitCount 1", () => {
    const onConfirm = vi.fn();
    render(<RoomStep availability={availability} initialRoomTypeId={null} onConfirm={onConfirm} onBack={vi.fn()} />);

    // The fixture's WHOLE type is free (bookableWholeSpaces: 1) — it must be selectable, and
    // there is no bed-count input for it at all.
    const wholeOption = screen.getByLabelText(/PRIVATE-DOUBLE/);
    expect(wholeOption).not.toBeDisabled();
    fireEvent.click(wholeOption);
    expect(screen.queryByLabelText('Beds')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /next: guest/i }));
    expect(onConfirm).toHaveBeenCalledWith(availability.roomTypes[1].roomTypeId, 1);
  });

  it('disables a room type with zero range-wide bookable spaces', () => {
    const soldOut: PropertyAvailabilityResponse = {
      ...availability,
      roomTypes: [{ ...availability.roomTypes[1], bookableWholeSpaces: 0 }],
    };
    render(<RoomStep availability={soldOut} initialRoomTypeId={null} onConfirm={vi.fn()} onBack={vi.fn()} />);

    expect(screen.getByLabelText(/PRIVATE-DOUBLE/)).toBeDisabled();
    expect(screen.getByText(/sold out/i)).toBeInTheDocument();
  });

  it('requires a room type to be picked before advancing', () => {
    const onConfirm = vi.fn();
    render(<RoomStep availability={availability} initialRoomTypeId={null} onConfirm={onConfirm} onBack={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: /next: guest/i }));

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
