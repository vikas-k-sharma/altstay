// Mirrors backend/src/main/java/com/altstay/api/booking/BookingStatus.java's canTransitionTo —
// deliberately, in this one file (phase-6 §7.3). Two copies of a state machine drift; the server
// is authoritative regardless, so a drift here produces a 409 rather than a wrong write. Note the
// one deviation from the "obvious" diagram: CHECKED_IN -> CANCELLED is legal (a stay voided after
// check-in — a payment that never cleared, someone asked to leave — is a real front-desk event).

export type BookingStatusValue = 'BOOKED' | 'CHECKED_IN' | 'CHECKED_OUT' | 'CANCELLED' | 'NO_SHOW';

const LEGAL_TRANSITIONS: Record<BookingStatusValue, readonly BookingStatusValue[]> = {
  BOOKED: ['CHECKED_IN', 'CANCELLED', 'NO_SHOW'],
  CHECKED_IN: ['CHECKED_OUT', 'CANCELLED'],
  CHECKED_OUT: [],
  CANCELLED: [],
  NO_SHOW: [],
};

const LABELS: Record<BookingStatusValue, string> = {
  BOOKED: 'Booked',
  CHECKED_IN: 'Checked in',
  CHECKED_OUT: 'Checked out',
  CANCELLED: 'Cancelled',
  NO_SHOW: 'No-show',
};

// Presentation only (phase-6 §8.2) — never colour alone, every chip also carries its label.
export type ChipTreatment = 'accent-outline' | 'success-filled' | 'muted' | 'muted-strike' | 'warning';

const CHIP_TREATMENTS: Record<BookingStatusValue, ChipTreatment> = {
  BOOKED: 'accent-outline',
  CHECKED_IN: 'success-filled',
  CHECKED_OUT: 'muted',
  CANCELLED: 'muted-strike',
  NO_SHOW: 'warning',
};

export function legalTransitionsFrom(status: BookingStatusValue): readonly BookingStatusValue[] {
  return LEGAL_TRANSITIONS[status];
}

export function statusLabel(status: BookingStatusValue): string {
  return LABELS[status];
}

export function statusChipTreatment(status: BookingStatusValue): ChipTreatment {
  return CHIP_TREATMENTS[status];
}
