import { HybridDiagram } from './HybridDiagram';

// phase-7 §5.1 block 5 — the differentiator, shown not asserted. §5.1: "a small diagram earns
// its place here and nowhere else on the site." 2026-09-05: the worked example is still the dorm,
// because it is the sharpest version of the conflict and the one a diagram can show in six cells.
// What changed is the sentence after it, which now names the same rule in the other property
// types — the constraint is on the space, not on the word "dorm".
export function HybridExplainerSection() {
  return (
    <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
      <div className="flex flex-wrap items-center gap-3">
        <p className="text-xs font-medium uppercase tracking-wide text-text-muted">The difference</p>
        <span className="rounded-full bg-surface-muted px-2.5 py-1 text-xs font-medium uppercase tracking-wide text-text-muted">
          In build
        </span>
      </div>
      <h2 className="mt-3 max-w-2xl text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
        One space. Two ways to sell it. Never both at once.
      </h2>
      <p className="mt-3 max-w-2xl text-sm leading-relaxed text-text-muted">
        Room 3 has six beds. You can sell six dorm beds, or you can sell the room whole as a
        private double. The moment either side sells, the other has to disappear for those nights
        — and that is the calculation you are currently doing in your head at 11 PM. The same rule
        is what a camp needs for a four-bunk tent that also goes whole to a couple, and what a
        homestay needs for rooms let one at a time until someone books the house. This is the
        model being built; today it&apos;s a diagram, not a live calendar.
      </p>

      <div className="mt-8">
        <HybridDiagram />
      </div>
    </section>
  );
}
