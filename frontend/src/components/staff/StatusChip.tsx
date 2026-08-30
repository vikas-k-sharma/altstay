import { statusLabel, statusChipTreatment, type BookingStatusValue } from '@/lib/staff/bookingStatus';

// Never colour alone (phase-6 §8.2) — every chip carries its label; CANCELLED additionally
// strikes through, so the released-allocation section can reuse the same visual language.
const TREATMENT_CLASSES: Record<string, string> = {
  'accent-outline': 'border border-accent text-accent',
  'success-filled': 'bg-success text-white',
  muted: 'bg-surface-muted text-text-muted',
  'muted-strike': 'bg-surface-muted text-text-muted line-through',
  warning: 'bg-warning text-white',
};

export function StatusChip({ status }: { status: BookingStatusValue }) {
  const treatment = statusChipTreatment(status);
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${TREATMENT_CLASSES[treatment]}`}
    >
      {statusLabel(status)}
    </span>
  );
}
