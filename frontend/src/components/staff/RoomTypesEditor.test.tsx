import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { RoomTypesEditor } from './RoomTypesEditor';
import roomTypeFixture from '@/lib/contracts/__fixtures__/room-type.json';
import type { RoomTypeDto } from '@/lib/contracts/inventory';

const roomTypes = [roomTypeFixture as RoomTypeDto];
const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh }),
}));

describe('RoomTypesEditor', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    refresh.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('lists existing room types with their rate formatted in the property currency', () => {
    render(<RoomTypesEditor propertySlug="driftwood-goa" currencyCode="INR" roomTypes={roomTypes} />);
    expect(screen.getByText('MIXED-6')).toBeInTheDocument();
    expect(screen.getByText(/₹650.00/)).toBeInTheDocument();
  });

  it('creates a room type, converting the entered rate to minor units', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(roomTypeFixture), { status: 201 }));

    render(<RoomTypesEditor propertySlug="driftwood-goa" currencyCode="INR" roomTypes={[]} />);
    const form = screen.getByRole('button', { name: /add room type/i }).closest('form')!;
    fireEvent.change(within(form).getByLabelText('Code'), { target: { value: 'PRIVATE-2' } });
    fireEvent.change(within(form).getByLabelText('Name'), { target: { value: 'Private double' } });
    fireEvent.change(within(form).getByLabelText('Base rate'), { target: { value: '3500' } });
    fireEvent.click(screen.getByRole('button', { name: /add room type/i }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    const body = JSON.parse((vi.mocked(global.fetch).mock.calls[0][1] as RequestInit).body as string);
    expect(body.baseRateMinor).toBe(350000);
    expect(body.code).toBe('PRIVATE-2');
  });

  it('edits an existing room type via PUT to its own id', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(roomTypeFixture), { status: 200 }));

    render(<RoomTypesEditor propertySlug="driftwood-goa" currencyCode="INR" roomTypes={roomTypes} />);
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe(
      `/api/console/properties/driftwood-goa/room-types/${roomTypeFixture.id}`
    );
  });
});
