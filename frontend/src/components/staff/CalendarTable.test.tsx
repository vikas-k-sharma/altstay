import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CalendarTable } from './CalendarTable';
import availabilityFixture from '@/lib/contracts/__fixtures__/availability.json';
import type { RoomTypeAvailabilityDto } from '@/lib/contracts/availability';

const roomTypes = availabilityFixture.roomTypes as RoomTypeAvailabilityDto[];

describe('CalendarTable', () => {
  it('renders PER_UNIT as available/total units', () => {
    render(<CalendarTable roomTypes={roomTypes} currency="INR" />);
    // PER_UNIT fixture row: 4 available / 6 total.
    expect(screen.getByText('4 / 6')).toBeInTheDocument();
  });

  it('renders WHOLE as available/total spaces per day, plus the range-wide bookable count in the row header — never the per-day count there', () => {
    render(<CalendarTable roomTypes={roomTypes} currency="INR" />);
    // WHOLE fixture row: 1 available / 1 total space that night.
    expect(screen.getByText('1 / 1')).toBeInTheDocument();
    // bookableWholeSpaces (range-wide) in the row header, distinct from the per-day cell above.
    expect(screen.getByText('1 bookable whole')).toBeInTheDocument();
  });

  it('shows a WHOLE room type as unavailable (0/total) when a bed in its space is sold', () => {
    const soldOut = roomTypes.map((rt) =>
      rt.saleMode === 'WHOLE'
        ? { ...rt, bookableWholeSpaces: 0, days: rt.days.map((d) => ({ ...d, availableSpaces: 0 })) }
        : rt
    );
    render(<CalendarTable roomTypes={soldOut} currency="INR" />);
    expect(screen.getByText('0 / 1')).toBeInTheDocument();
    expect(screen.getByText('0 bookable whole')).toBeInTheDocument();
  });

  it('links each cell to the new-booking wizard pre-filled with room type and date', () => {
    render(<CalendarTable roomTypes={roomTypes} currency="INR" />);
    const link = screen.getByRole('link', { name: /4 \/ 6/ });
    expect(link).toHaveAttribute(
      'href',
      `/console/bookings/new?roomTypeId=${roomTypes[0].roomTypeId}&date=${roomTypes[0].days[0].date}`
    );
  });

  it('formats the rate with the given currency', () => {
    render(<CalendarTable roomTypes={roomTypes} currency="INR" />);
    expect(screen.getByText(/₹650/)).toBeInTheDocument();
  });
});
