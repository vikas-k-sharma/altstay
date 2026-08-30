'use client';

import { useEffect, useReducer } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  bookingWizardReducer,
  initialWizardState,
  type WizardStep,
} from '@/lib/staff/bookingWizardReducer';
import { consoleFetch } from '@/lib/staff/clientFetch';
import { QuoteResponseSchema } from '@/lib/contracts/rate';
import { BookingResponseSchema } from '@/lib/contracts/booking';
import type { PropertyAvailabilityResponse } from '@/lib/contracts/availability';
import { DatesStep } from './wizard/DatesStep';
import { RoomStep } from './wizard/RoomStep';
import { GuestStep } from './wizard/GuestStep';
import { ReviewStep } from './wizard/ReviewStep';

const STEP_LABELS: Record<WizardStep, string> = {
  DATES: 'Dates',
  ROOM: 'Room',
  GUEST: 'Guest',
  REVIEW: 'Review',
  CREATED: 'Done',
};

export function BookingWizard({
  property,
  today,
  initialCheckIn,
  initialCheckOut,
  initialRoomTypeId,
  availability,
  startAtRoom,
}: {
  property: { id: string };
  today: string;
  initialCheckIn: string;
  initialCheckOut: string;
  initialRoomTypeId: string | null;
  availability: PropertyAvailabilityResponse | null;
  startAtRoom: boolean;
}) {
  const router = useRouter();
  const [state, dispatch] = useReducer(
    bookingWizardReducer,
    { checkIn: initialCheckIn, checkOut: initialCheckOut, roomTypeId: initialRoomTypeId, availability, startAtRoom },
    initialWizardState
  );

  // page.tsx re-fetches availability whenever `from`/`to` change in the URL (or on
  // router.refresh() after a 409) — this just carries the new prop into the reducer without
  // resetting anything else the user has already chosen.
  useEffect(() => {
    dispatch({ type: 'AVAILABILITY_UPDATED', availability });
  }, [availability]);

  async function handleFetchQuote() {
    if (!state.roomTypeId) {
      return;
    }
    try {
      const response = await consoleFetch('/api/console/quote', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          propertyId: property.id,
          roomTypeId: state.roomTypeId,
          checkIn: state.checkIn,
          checkOut: state.checkOut,
          unitCount: state.unitCount,
        }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => undefined);
        dispatch({ type: 'ERROR', message: body?.detail ?? 'Could not fetch a quote. Please try again.' });
        return;
      }
      const quote = QuoteResponseSchema.parse(await response.json());
      dispatch({ type: 'QUOTE_LOADED', quote });
    } catch {
      dispatch({ type: 'ERROR', message: 'Could not reach AltStay. Please try again.' });
    }
  }

  async function handleConfirmBooking() {
    if (!state.roomTypeId || !state.guest || !state.idempotencyKey) {
      return;
    }
    const response = await consoleFetch('/api/console/bookings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        propertyId: property.id,
        propertySlug: null,
        guest: state.guest,
        checkIn: state.checkIn,
        checkOut: state.checkOut,
        adults: state.adults,
        children: state.children,
        source: 'DIRECT',
        lines: [
          {
            roomTypeId: state.roomTypeId,
            spaceId: null,
            checkIn: null,
            checkOut: null,
            unitCount: state.unitCount,
            amountMinor: null,
          },
        ],
        idempotencyKey: state.idempotencyKey,
        notes: null,
      }),
    });

    if (response.status === 409) {
      const body = await response.json().catch(() => undefined);
      dispatch({
        type: 'CONFLICT',
        message: body?.detail ?? 'That could not be booked — availability changed. Pick again.',
      });
      // The bed that was free a moment ago may not be — re-fetch rather than show a stale grid.
      router.refresh();
      return;
    }
    if (!response.ok) {
      const body = await response.json().catch(() => undefined);
      dispatch({ type: 'ERROR', message: body?.detail ?? 'Could not create the booking. Please try again.' });
      return;
    }

    const booking = BookingResponseSchema.parse(await response.json());
    dispatch({ type: 'BOOKING_CREATED', reference: booking.reference });
  }

  if (state.step === 'CREATED' && state.createdBooking) {
    return (
      <div className="max-w-md space-y-3">
        <p className="text-lg font-semibold">Booking created.</p>
        <Link href={`/console/bookings/${state.createdBooking.reference}`} className="text-accent hover:underline">
          View {state.createdBooking.reference} →
        </Link>
      </div>
    );
  }

  const roomTypeCode = state.availability?.roomTypes.find((rt) => rt.roomTypeId === state.roomTypeId)?.code ?? '';

  return (
    <div className="space-y-4">
      <ol className="flex gap-2 text-xs text-text-muted">
        {(['DATES', 'ROOM', 'GUEST', 'REVIEW'] as const).map((step) => (
          <li key={step} className={step === state.step ? 'font-semibold text-accent' : ''}>
            {STEP_LABELS[step]}
          </li>
        ))}
      </ol>

      {/* ReviewStep renders `error` itself, inline by the Confirm button — this banner only
          covers the other steps, e.g. a 409 at REVIEW landing back on ROOM. */}
      {state.error && state.step !== 'REVIEW' && (
        <p role="alert" className="text-sm text-danger">
          {state.error}
        </p>
      )}

      {state.step === 'DATES' && (
        <DatesStep
          today={today}
          initialCheckIn={state.checkIn}
          initialCheckOut={state.checkOut}
          initialAdults={state.adults}
          initialChildren={state.children}
          onConfirm={(checkIn, checkOut, adults, children) => {
            dispatch({ type: 'DATES_CONFIRMED', checkIn, checkOut, adults, children });
            const params = new URLSearchParams({ from: checkIn, to: checkOut });
            router.replace(`/console/bookings/new?${params.toString()}`);
          }}
        />
      )}

      {state.step === 'ROOM' && (
        <RoomStep
          availability={state.availability}
          initialRoomTypeId={state.roomTypeId}
          onConfirm={(roomTypeId, unitCount) => dispatch({ type: 'ROOM_CONFIRMED', roomTypeId, unitCount })}
          onBack={() => dispatch({ type: 'BACK', to: 'DATES' })}
        />
      )}

      {state.step === 'GUEST' && (
        <GuestStep
          onConfirm={(guest) =>
            dispatch({ type: 'GUEST_CONFIRMED', guest, idempotencyKey: crypto.randomUUID() })
          }
          onBack={() => dispatch({ type: 'BACK', to: 'ROOM' })}
        />
      )}

      {state.step === 'REVIEW' && state.guest && state.roomTypeId && (
        <ReviewStep
          checkIn={state.checkIn}
          checkOut={state.checkOut}
          roomTypeCode={roomTypeCode}
          guest={state.guest}
          quote={state.quote}
          error={state.error}
          onFetchQuote={handleFetchQuote}
          onConfirm={handleConfirmBooking}
          onBack={() => dispatch({ type: 'BACK', to: 'GUEST' })}
        />
      )}
    </div>
  );
}
