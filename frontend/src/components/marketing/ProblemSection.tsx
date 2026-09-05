// phase-7 §5.1 block 2 — roadmap §1's two mismatches, stated as an owner experiences them.
// 2026-09-05: mismatch 01 was written entirely in dorm vocabulary, so it read as a hostel
// problem. It is not — it is the same problem in a homestay, a camp and a co-living house, and
// the examples now say so. Deliberately left out: the retreat *bundle* (roadmap §5.2, "a retreat
// is a bundle, not a room type"). It is a real mismatch, but phase-5 §13 defers building it, and
// this block sits directly above three pillars that claim to solve what it names.
const MISMATCHES = [
  {
    number: '01',
    title: 'The same space is two products. Your PMS knows one.',
    body: 'Room 3 is a six-bed dorm Monday to Thursday. On Saturday a family takes the whole thing as a private double. A homestay lets two guest rooms one at a time, then the whole house to a wedding party. A camp sells a bunk in a tent that also goes whole to a couple. Every hotel PMS treats a room as one sellable unit, so the real availability lives in a spreadsheet on your laptop — and the spreadsheet is why you stop selling the last two beds after 9 PM rather than risk double-booking them.',
  },
  {
    number: '02',
    title: 'You are the WhatsApp integration.',
    body: "Guests ask on WhatsApp, before and during the stay. Airport pickup at 2 AM. Check-in time. Gate code. Whether the kitchen is still open. No PMS lives there, so the answers come from you, personally, from bed. The questions repeat, and the answers are already written down somewhere.",
  },
] as const;

export function ProblemSection() {
  return (
    <section className="border-t border-border bg-surface-muted">
      <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">
          Why a hotel PMS keeps failing you
        </p>
        <h2 className="mt-3 max-w-2xl text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
          Two mismatches, and you already work around both of them by hand.
        </h2>

        <div className="mt-10 grid gap-6 sm:grid-cols-2">
          {MISMATCHES.map((item) => (
            <div key={item.number} className="rounded-2xl border border-border bg-surface p-6">
              <p className="text-xs font-medium text-text-muted">{item.number}</p>
              <h3 className="mt-2 text-lg font-semibold text-foreground">{item.title}</h3>
              <p className="mt-3 text-sm leading-relaxed text-text-muted">{item.body}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
