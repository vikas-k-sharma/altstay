import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { HybridMappingEditor } from './HybridMappingEditor';
import roomTypeFixture from '@/lib/contracts/__fixtures__/room-type.json';
import spaceFixture from '@/lib/contracts/__fixtures__/space.json';
import type { RoomTypeDto, SpaceDto } from '@/lib/contracts/inventory';

const mappedRoomType = roomTypeFixture as RoomTypeDto; // spaceIds includes spaceFixture.id
const space = spaceFixture as SpaceDto;
const unmappedRoomType: RoomTypeDto = { ...mappedRoomType, id: 'unmapped-rt', code: 'PRIVATE-2', name: 'Private double', spaceIds: [] };

const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh }),
}));

describe('HybridMappingEditor', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    refresh.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('shows the zero-mapping warning for a space sold as nothing', () => {
    const unsoldSpace = { ...space, id: 'unsold-space' };
    render(<HybridMappingEditor spaces={[unsoldSpace]} roomTypes={[unmappedRoomType]} />);
    expect(screen.getByText(/this room cannot be sold/i)).toBeInTheDocument();
  });

  it('shows the currently mapped room type as a removable chip', () => {
    render(<HybridMappingEditor spaces={[space]} roomTypes={[mappedRoomType]} />);
    expect(screen.getByText(mappedRoomType.name)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: `Remove ${mappedRoomType.name} from ${space.name}` })).toBeInTheDocument();
  });

  it('adds a mapping via POST to the hybrid-mapping BFF route', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 201 }));

    render(<HybridMappingEditor spaces={[space]} roomTypes={[mappedRoomType, unmappedRoomType]} />);
    fireEvent.change(screen.getByLabelText(`Add a room type to ${space.name}`), {
      target: { value: unmappedRoomType.id },
    });
    fireEvent.click(screen.getByRole('button', { name: 'add' }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe(
      `/api/console/room-types/${unmappedRoomType.id}/spaces/${space.id}`
    );
    expect(vi.mocked(global.fetch).mock.calls[0][1]).toEqual(expect.objectContaining({ method: 'POST' }));
  });

  it('removes a mapping via DELETE to the hybrid-mapping BFF route', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(null, { status: 204 }));

    render(<HybridMappingEditor spaces={[space]} roomTypes={[mappedRoomType]} />);
    fireEvent.click(screen.getByRole('button', { name: `Remove ${mappedRoomType.name} from ${space.name}` }));

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(vi.mocked(global.fetch).mock.calls[0][0]).toBe(
      `/api/console/room-types/${mappedRoomType.id}/spaces/${space.id}`
    );
    expect(vi.mocked(global.fetch).mock.calls[0][1]).toEqual(expect.objectContaining({ method: 'DELETE' }));
  });
});
