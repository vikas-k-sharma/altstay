import type { GuestDto } from '@/lib/contracts/booking';
import type { PropertyAvailabilityResponse } from '@/lib/contracts/availability';
import type { QuoteResponse } from '@/lib/contracts/rate';

// One reducer for the whole wizard (phase-6 §4.6):
//   DATES ──► ROOM ──► GUEST ──► REVIEW ──► CREATED
//     ▲         ▲                   │
//     └─────────┴───────────────────┘  a 409 at REVIEW returns to ROOM
export type WizardStep = 'DATES' | 'ROOM' | 'GUEST' | 'REVIEW' | 'CREATED';

export type WizardState = {
  step: WizardStep;
  checkIn: string;
  checkOut: string;
  adults: number;
  children: number;
  roomTypeId: string | null;
  unitCount: number;
  guest: GuestDto | null;
  availability: PropertyAvailabilityResponse | null;
  quote: QuoteResponse | null;
  idempotencyKey: string | null;
  error: string | null;
  createdBooking: { reference: string } | null;
};

export type WizardAction =
  | { type: 'DATES_CONFIRMED'; checkIn: string; checkOut: string; adults: number; children: number }
  | { type: 'ROOM_CONFIRMED'; roomTypeId: string; unitCount: number }
  | { type: 'GUEST_CONFIRMED'; guest: GuestDto; idempotencyKey: string }
  | { type: 'BACK'; to: WizardStep }
  | { type: 'AVAILABILITY_UPDATED'; availability: PropertyAvailabilityResponse | null }
  | { type: 'QUOTE_LOADED'; quote: QuoteResponse }
  | { type: 'CONFLICT'; message: string }
  | { type: 'ERROR'; message: string }
  | { type: 'BOOKING_CREATED'; reference: string };

export function initialWizardState(seed: {
  checkIn: string;
  checkOut: string;
  roomTypeId: string | null;
  availability: PropertyAvailabilityResponse | null;
  startAtRoom: boolean;
}): WizardState {
  return {
    step: seed.startAtRoom ? 'ROOM' : 'DATES',
    checkIn: seed.checkIn,
    checkOut: seed.checkOut,
    adults: 1,
    children: 0,
    roomTypeId: seed.roomTypeId,
    unitCount: 1,
    guest: null,
    availability: seed.availability,
    quote: null,
    idempotencyKey: null,
    error: null,
    createdBooking: null,
  };
}

export function bookingWizardReducer(state: WizardState, action: WizardAction): WizardState {
  switch (action.type) {
    case 'DATES_CONFIRMED':
      return {
        ...state,
        step: 'ROOM',
        checkIn: action.checkIn,
        checkOut: action.checkOut,
        adults: action.adults,
        children: action.children,
        roomTypeId: null,
        quote: null,
        error: null,
      };
    case 'ROOM_CONFIRMED':
      return { ...state, step: 'GUEST', roomTypeId: action.roomTypeId, unitCount: action.unitCount, error: null };
    case 'GUEST_CONFIRMED':
      // The idempotencyKey is generated once, here, on the way into REVIEW — and only here.
      // Every retry of *this* attempt (a double-click, a retry-after-network-error button) reuses
      // whatever is already in state; only a fresh pass through GUEST_CONFIRMED mints a new one.
      return { ...state, step: 'REVIEW', guest: action.guest, idempotencyKey: action.idempotencyKey, error: null };
    case 'BACK':
      return { ...state, step: action.to, error: null };
    case 'AVAILABILITY_UPDATED':
      return { ...state, availability: action.availability };
    case 'QUOTE_LOADED':
      return { ...state, quote: action.quote, error: null };
    case 'CONFLICT':
      // 409 is an ordinary outcome, not an error state (§4.6) — back to ROOM, quote discarded,
      // the caller is responsible for also triggering a fresh availability fetch.
      return { ...state, step: 'ROOM', quote: null, error: action.message };
    case 'ERROR':
      return { ...state, error: action.message };
    case 'BOOKING_CREATED':
      return { ...state, step: 'CREATED', createdBooking: { reference: action.reference }, error: null };
    default:
      return state;
  }
}
