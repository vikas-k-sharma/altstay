import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ReviewStep } from './ReviewStep';
import quoteFixture from '@/lib/contracts/__fixtures__/quote.json';
import type { GuestDto } from '@/lib/contracts/booking';

const guest: GuestDto = {
  id: null,
  fullName: 'Arjun Mehta',
  email: 'arjun@example.com',
  phone: null,
  countryCode: null,
  dateOfBirth: null,
  notes: null,
};

describe('ReviewStep', () => {
  it('fetches the quote exactly once on mount', () => {
    const onFetchQuote = vi.fn();
    render(
      <ReviewStep
        checkIn="2026-08-30"
        checkOut="2026-09-02"
        roomTypeCode="MIXED-6"
        guest={guest}
        quote={null}
        error={null}
        onFetchQuote={onFetchQuote}
        onConfirm={vi.fn()}
        onBack={vi.fn()}
      />
    );

    expect(onFetchQuote).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/fetching the quote/i)).toBeInTheDocument();
  });

  it('renders the nightly breakdown and total once the quote arrives, and enables Confirm', () => {
    render(
      <ReviewStep
        checkIn="2026-08-30"
        checkOut="2026-09-02"
        roomTypeCode="MIXED-6"
        guest={guest}
        quote={quoteFixture}
        error={null}
        onFetchQuote={vi.fn()}
        onConfirm={vi.fn()}
        onBack={vi.fn()}
      />
    );

    expect(screen.getAllByText(/₹650.00/).length).toBe(3);
    expect(screen.getByText(/₹2,184.00/)).toBeInTheDocument(); // total
    expect(screen.getByRole('button', { name: /confirm booking/i })).not.toBeDisabled();
  });

  it('disables Confirm until the quote has loaded', () => {
    render(
      <ReviewStep
        checkIn="2026-08-30"
        checkOut="2026-09-02"
        roomTypeCode="MIXED-6"
        guest={guest}
        quote={null}
        error={null}
        onFetchQuote={vi.fn()}
        onConfirm={vi.fn()}
        onBack={vi.fn()}
      />
    );

    expect(screen.getByRole('button', { name: /confirm booking/i })).toBeDisabled();
  });

  it('calls onConfirm when Confirm is clicked', async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    render(
      <ReviewStep
        checkIn="2026-08-30"
        checkOut="2026-09-02"
        roomTypeCode="MIXED-6"
        guest={guest}
        quote={quoteFixture}
        error={null}
        onFetchQuote={vi.fn()}
        onConfirm={onConfirm}
        onBack={vi.fn()}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: /confirm booking/i }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('shows a passed-in error, e.g. a 409 conflict message', () => {
    render(
      <ReviewStep
        checkIn="2026-08-30"
        checkOut="2026-09-02"
        roomTypeCode="MIXED-6"
        guest={guest}
        quote={null}
        error="That bed just went. Pick again."
        onFetchQuote={vi.fn()}
        onConfirm={vi.fn()}
        onBack={vi.fn()}
      />
    );

    expect(screen.getByRole('alert')).toHaveTextContent('That bed just went. Pick again.');
  });
});
