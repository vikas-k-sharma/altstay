import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { RateCalendarEditor } from './RateCalendarEditor';
import roomTypeFixture from '@/lib/contracts/__fixtures__/room-type.json';
import ratePlanFixture from '@/lib/contracts/__fixtures__/rate-plan.json';
import type { RoomTypeDto } from '@/lib/contracts/inventory';
import type { RatePlanDto, RateCalendarDto } from '@/lib/contracts/rate';

const roomType = roomTypeFixture as RoomTypeDto; // baseRateMinor 65000, INR
const ratePlan = ratePlanFixture as RatePlanDto; // roomTypeId matches roomType.id
const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh }),
}));

describe('RateCalendarEditor', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    refresh.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('renders the base rate in muted text for a date with no override', () => {
    render(
      <RateCalendarEditor
        propertySlug="driftwood-goa"
        currencyCode="INR"
        roomTypes={[roomType]}
        ratePlans={[ratePlan]}
        selectedRatePlan={ratePlan}
        calendar={[]}
        monthStart="2026-08-01"
        monthEnd="2026-08-03"
      />
    );

    // 3 days, all falling back to baseRateMinor (650.00) since calendar is empty.
    expect(screen.getAllByText('₹650.00')).toHaveLength(3);
    expect(screen.getByText(/fall back to the room type/i)).toBeInTheDocument();
  });

  it('renders an override amount instead of the base rate for a date that has one', () => {
    const calendar: RateCalendarDto[] = [{ stayDate: '2026-08-02', amountMinor: 90000 }];
    render(
      <RateCalendarEditor
        propertySlug="driftwood-goa"
        currencyCode="INR"
        roomTypes={[roomType]}
        ratePlans={[ratePlan]}
        selectedRatePlan={ratePlan}
        calendar={calendar}
        monthStart="2026-08-01"
        monthEnd="2026-08-03"
      />
    );

    expect(screen.getAllByText('₹650.00')).toHaveLength(2);
    expect(screen.getByText('₹900.00')).toBeInTheDocument();
  });

  it('sets a rate for an inclusive range in one PUT request, converting major to minor', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 }));

    render(
      <RateCalendarEditor
        propertySlug="driftwood-goa"
        currencyCode="INR"
        roomTypes={[roomType]}
        ratePlans={[ratePlan]}
        selectedRatePlan={ratePlan}
        calendar={[]}
        monthStart="2026-12-01"
        monthEnd="2026-12-03"
      />
    );

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-12-24' } });
    fireEvent.change(screen.getByLabelText('To (inclusive)'), { target: { value: '2026-12-26' } });
    fireEvent.change(screen.getByLabelText('Rate'), { target: { value: '1200' } });
    fireEvent.click(screen.getByRole('button', { name: /^set rate$/i }));

    await waitFor(() => expect(screen.getByText('Saved.')).toBeInTheDocument());
    expect(refresh).toHaveBeenCalled();

    const [url, init] = vi.mocked(global.fetch).mock.calls[0];
    expect(url).toBe(`/api/console/rate-plans/${ratePlan.id}/calendar`);
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ from: '2026-12-24', to: '2026-12-26', amountMinor: 120000 });
  });

  it('creates a rate plan for a chosen room type', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(ratePlan), { status: 201 }));

    render(
      <RateCalendarEditor
        propertySlug="driftwood-goa"
        currencyCode="INR"
        roomTypes={[roomType]}
        ratePlans={[]}
        selectedRatePlan={null}
        calendar={[]}
        monthStart="2026-08-01"
        monthEnd="2026-08-03"
      />
    );

    fireEvent.change(screen.getByLabelText('Room type'), { target: { value: roomType.id } });
    fireEvent.change(screen.getByLabelText('Code'), { target: { value: 'STANDARD' } });
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Standard rate' } });
    fireEvent.click(screen.getByRole('button', { name: /create rate plan/i }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    const [url, init] = vi.mocked(global.fetch).mock.calls[0];
    expect(url).toBe('/api/console/properties/driftwood-goa/rate-plans');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ roomTypeId: roomType.id, code: 'STANDARD', name: 'Standard rate', isDefault: false });
  });

  it('shows the backend refusal when a second default plan collides', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify({ detail: 'That change conflicts with something already recorded.' }), { status: 409 })
    );

    render(
      <RateCalendarEditor
        propertySlug="driftwood-goa"
        currencyCode="INR"
        roomTypes={[roomType]}
        ratePlans={[]}
        selectedRatePlan={null}
        calendar={[]}
        monthStart="2026-08-01"
        monthEnd="2026-08-03"
      />
    );

    fireEvent.change(screen.getByLabelText('Room type'), { target: { value: roomType.id } });
    fireEvent.change(screen.getByLabelText('Code'), { target: { value: 'STANDARD' } });
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Standard rate' } });
    fireEvent.click(screen.getByLabelText('Default for this room type'));
    fireEvent.click(screen.getByRole('button', { name: /create rate plan/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('conflicts with something already recorded');
    expect(refresh).not.toHaveBeenCalled();
  });
});
