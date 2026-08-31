// phase-7 §5.1 block 5 — roadmap §5's crux, shown not asserted: one physical room, sold two
// mutually exclusive ways. Two static snapshots (a dorm night vs. a private night), not an
// interactive toggle — §7.4 caps motion at scroll entrance, nothing that keeps moving.
const DORM_NIGHT_BEDS = [
  { label: '1', status: 'sold' },
  { label: '2', status: 'sold' },
  { label: '3', status: 'sold' },
  { label: '4', status: 'sold' },
  { label: '5', status: 'free' },
  { label: '6', status: 'free' },
] as const;

const PRIVATE_NIGHT_BEDS = [
  { label: '1', status: 'held' },
  { label: '2', status: 'held' },
  { label: '3', status: 'held' },
  { label: '4', status: 'held' },
  { label: '5', status: 'held' },
  { label: '6', status: 'held' },
] as const;

function BedCell({ label, status }: { label: string; status: 'sold' | 'free' | 'held' }) {
  const styles =
    status === 'free'
      ? 'border-accent/40 bg-accent-quiet text-accent'
      : 'border-border bg-surface-muted text-text-muted';
  return (
    <div className={`flex flex-col items-center justify-center rounded-lg border py-3 text-xs ${styles}`}>
      <span className="font-medium text-foreground">{label}</span>
      <span>{status}</span>
    </div>
  );
}

export function HybridDiagram() {
  return (
    <div className="grid gap-6 sm:grid-cols-2">
      <div
        role="group"
        aria-label="Tuesday, dorm night"
        className="rounded-2xl border border-border bg-surface p-5"
      >
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Tue 14 Oct · dorm night</p>
        <div className="mt-4 grid grid-cols-3 gap-2">
          {DORM_NIGHT_BEDS.map((bed) => (
            <BedCell key={bed.label} label={bed.label} status={bed.status} />
          ))}
        </div>
        <p className="mt-4 text-sm text-text-muted">4 beds sold · 2 still sellable at ₹650</p>
        <p className="mt-2 text-sm text-foreground">
          Private double, ₹2,400 — <span className="text-danger">unavailable</span>: the room can&apos;t go
          whole while four of six beds are sold.
        </p>
      </div>

      <div
        role="group"
        aria-label="Saturday, private night"
        className="rounded-2xl border border-border bg-surface p-5"
      >
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">Sat 18 Oct · private night</p>
        <div className="mt-4 grid grid-cols-3 gap-2">
          {PRIVATE_NIGHT_BEDS.map((bed) => (
            <BedCell key={bed.label} label={bed.label} status={bed.status} />
          ))}
        </div>
        <p className="mt-4 text-sm text-foreground">Private double, ₹2,400 — sold.</p>
        <p className="mt-2 text-sm text-text-muted">
          One booking, one guest, six beds off the market for the night.
        </p>
      </div>
    </div>
  );
}
