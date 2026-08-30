import { describe, it, expect } from 'vitest';
import { NextRequest } from 'next/server';
import { proxy } from './proxy';

function requestFor(path: string, cookie?: string) {
  return new NextRequest(`http://localhost:3000${path}`, {
    headers: cookie ? { Cookie: cookie } : undefined,
  });
}

describe('proxy (optimistic /console gate)', () => {
  it('lets /console/login through unconditionally, to avoid a redirect loop', () => {
    const response = proxy(requestFor('/console/login'));
    expect(response.headers.get('location')).toBeNull();
  });

  it('lets a request with a session cookie through', () => {
    const response = proxy(requestFor('/console/bookings', 'altstay_session=abc123'));
    expect(response.headers.get('location')).toBeNull();
  });

  it('redirects a cookie-less request to /console/login preserving the exact path as next=', () => {
    const response = proxy(requestFor('/console/bookings?status=BOOKED'));

    const location = response.headers.get('location');
    expect(location).not.toBeNull();
    const url = new URL(location!);
    expect(url.pathname).toBe('/console/login');
    expect(url.searchParams.get('next')).toBe('/console/bookings?status=BOOKED');
  });

  it('does not touch routes outside /console', () => {
    const response = proxy(requestFor('/concierge'));
    expect(response.headers.get('location')).toBeNull();
  });
});
