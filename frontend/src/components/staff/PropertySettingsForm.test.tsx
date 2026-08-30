import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PropertySettingsForm } from './PropertySettingsForm';
import propertyFixture from '@/lib/contracts/__fixtures__/property.json';
import amenitiesFixture from '@/lib/contracts/__fixtures__/amenities.json';
import type { PropertyResponse } from '@/lib/contracts/property';
import type { AmenityResponse } from '@/lib/contracts/amenity';

const property = propertyFixture as PropertyResponse;
const amenities = amenitiesFixture as AmenityResponse[];

const refresh = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh }),
}));

describe('PropertySettingsForm', () => {
  const originalFetch = global.fetch;

  beforeEach(() => {
    refresh.mockClear();
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('pre-fills from the property, converting taxRateBps to a percentage and the slug to read-only', () => {
    render(<PropertySettingsForm property={property} amenities={amenities} />);

    expect(screen.getByLabelText('Name')).toHaveValue('Driftwood Beach Hostel');
    expect(screen.getByLabelText('Tax rate (%)')).toHaveValue('12');
    expect(screen.getByLabelText('Slug')).toHaveValue('driftwood-goa');
    expect(screen.getByLabelText('Slug')).toBeDisabled();
    expect(screen.getByLabelText('Free Wi-Fi')).toBeChecked();
    expect(screen.getByLabelText('Air conditioning')).not.toBeChecked();
  });

  it('groups amenities by category', () => {
    render(<PropertySettingsForm property={property} amenities={amenities} />);
    expect(screen.getByText('Connectivity')).toBeInTheDocument();
    expect(screen.getByText('Food')).toBeInTheDocument();
    expect(screen.getByText('Comfort')).toBeInTheDocument();
  });

  it('saves every field, converting the tax percentage back to basis points', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(property), { status: 200 }));

    render(<PropertySettingsForm property={property} amenities={amenities} />);
    fireEvent.change(screen.getByLabelText('Tax rate (%)'), { target: { value: '18' } });
    fireEvent.click(screen.getByLabelText('Air conditioning'));
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(screen.getByText('Saved.')).toBeInTheDocument());
    expect(refresh).toHaveBeenCalled();

    const [url, init] = vi.mocked(global.fetch).mock.calls[0];
    expect(url).toBe('/api/console/properties/driftwood-goa');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.taxRateBps).toBe(1800);
    expect(body.amenities).toEqual(expect.arrayContaining(['WIFI', 'BREAKFAST', 'AC']));
    expect(body.legalName).toBeNull();
  });

  it('shows the backend refusal and does not refresh on failure', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(
      new Response(JSON.stringify({ detail: 'Access is denied' }), { status: 403 })
    );

    render(<PropertySettingsForm property={property} amenities={amenities} />);
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Access is denied');
    expect(refresh).not.toHaveBeenCalled();
  });
});
