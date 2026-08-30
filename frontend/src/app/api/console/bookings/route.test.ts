import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { NextRequest } from 'next/server';
import { POST } from './route';
import bookingFixture from '@/lib/contracts/__fixtures__/booking.json';

const validBody = {
  propertyId: '7ed13bba-74e2-4608-83c6-bedb10b9e5bd',
  propertySlug: null,
  guest: {
    id: null,
    fullName: 'New Guest',
    email: 'new@example.com',
    phone: null,
    countryCode: null,
    dateOfBirth: null,
    notes: null,
  },
  checkIn: '2026-08-30',
  checkOut: '2026-09-02',
  adults: 1,
  children: 0,
  source: 'DIRECT',
  lines: [
    { roomTypeId: 'd812b0fb-cd5d-4ed7-a4d2-0a12caf6b118', spaceId: null, checkIn: null, checkOut: null, unitCount: 1, amountMinor: null },
  ],
  idempotencyKey: '8f14e45f-ceea-4c9e-8e5c-8f3e2e2b8e2a',
  notes: null,
};

function bookingRequest(body: unknown) {
  return new NextRequest('http://localhost:3000/api/console/bookings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

describe('POST /api/console/bookings', () => {
  const originalFetch = global.fetch;
  const originalBackendUrl = process.env.BACKEND_URL;

  beforeEach(() => {
    process.env.BACKEND_URL = 'http://localhost:8080';
  });

  afterEach(() => {
    global.fetch = originalFetch;
    if (originalBackendUrl !== undefined) {
      process.env.BACKEND_URL = originalBackendUrl;
    } else {
      delete process.env.BACKEND_URL;
    }
  });

  it('returns 400 without calling upstream when there are no lines', async () => {
    global.fetch = vi.fn();
    const response = await POST(bookingRequest({ ...validBody, lines: [] }));
    expect(response.status).toBe(400);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it('creates a booking and validates the response', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(bookingFixture), { status: 201 }));

    const response = await POST(bookingRequest(validBody));
    const data = await response.json();

    expect(response.status).toBe(201);
    expect(data.reference).toBe('ALT7F3K9Q');
  });

  it('passes through a 409 no-availability unchanged', async () => {
    const problem = {
      type: 'https://api.altstay.com/errors/no-availability',
      title: 'No Availability',
      status: 409,
      detail: 'No beds available for MIXED-6',
    };
    global.fetch = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(problem), { status: 409 }));

    const response = await POST(bookingRequest(validBody));
    const data = await response.json();

    expect(response.status).toBe(409);
    expect(data.type).toBe(problem.type);
  });
});
