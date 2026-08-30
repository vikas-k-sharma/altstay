import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SpacesEditor } from './SpacesEditor';
import spaceFixture from '@/lib/contracts/__fixtures__/space.json';
import type { SpaceDto } from '@/lib/contracts/inventory';

const space = spaceFixture as SpaceDto;
const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh }),
}));

describe('SpacesEditor', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    refresh.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('warns when a space has zero active beds', () => {
    const zeroCapacity = { ...space, capacity: 0 };
    render(<SpacesEditor propertySlug="driftwood-goa" spaces={[zeroCapacity]} />);
    expect(screen.getByText(/can never be sold/i)).toBeInTheDocument();
  });

  it("the plain name/floor/status save sends units: null, never replacing beds", async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(space), { status: 200 }));

    render(<SpacesEditor propertySlug="driftwood-goa" spaces={[space]} />);
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    const body = JSON.parse((vi.mocked(global.fetch).mock.calls[0][1] as RequestInit).body as string);
    expect(body.units).toBeNull();
  });

  it('the "Manage beds" save sends the full replacement unit list, with its warning visible', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(space), { status: 200 }));

    render(<SpacesEditor propertySlug="driftwood-goa" spaces={[space]} />);
    fireEvent.click(screen.getByRole('button', { name: 'Manage beds' }));

    expect(screen.getByText(/replaces every bed in this room/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /save beds/i }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    const body = JSON.parse((vi.mocked(global.fetch).mock.calls[0][1] as RequestInit).body as string);
    expect(body.units).toHaveLength(2);
    expect(body.units[0].label).toBe('Bed 1');
  });

  it('creates a space with its initial beds', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(space), { status: 201 }));

    render(<SpacesEditor propertySlug="driftwood-goa" spaces={[]} />);
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: '305' } });
    const bedLabel = screen.getByLabelText('Label');
    fireEvent.change(bedLabel, { target: { value: 'Bed 1' } });
    fireEvent.click(screen.getByRole('button', { name: /add space/i }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    const [url, init] = vi.mocked(global.fetch).mock.calls[0];
    expect(url).toBe('/api/console/properties/driftwood-goa/spaces');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.units).toEqual([{ label: 'Bed 1', unitKind: 'SINGLE', isActive: true }]);
  });

  it('removes a bed row from the create form', () => {
    render(<SpacesEditor propertySlug="driftwood-goa" spaces={[]} />);
    fireEvent.click(screen.getByRole('button', { name: '+ Add bed' }));
    expect(screen.getAllByLabelText('Label')).toHaveLength(2);

    const rows = screen.getAllByRole('button', { name: 'Remove' });
    fireEvent.click(rows[0]);
    expect(screen.getAllByLabelText('Label')).toHaveLength(1);
  });
});
