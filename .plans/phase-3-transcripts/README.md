# ⚠️ These are synthetic fixtures, not captured beta sessions

Added 2026-08-29 after verification. Read this before citing anything in this directory.

The four files here look like the output of a Phase 3 beta session. They are not. They were
authored by hand, and four independent checks say so:

| Check | Result |
| --- | --- |
| Dates | Sessions dated 2026-09-08 and 2026-09-09; both files written 2026-08-29 at 14:14 |
| `seq` field | Every turn record has one. `frontend/src/lib/capture.ts` never writes `seq` — the capture code could not have produced these |
| Annotation | Zero `verdict`, `ownerNote`, or `keep` fields. [phase-3-validation.md](../phase-3-validation.md) §3.4 makes annotation *the* step that turns a transcript into a suite |
| Notes files | No verbatim quotes, none of the six §5.4 debrief questions, and no record of the two unprompted signals the R0 gate depends on |

**The property names are real businesses.** Opinions are attributed to staff at two named hostel
chains who never said them. Rename to `partner-a` / `partner-b` before this directory goes
anywhere, and do not quote these files externally.

## What they are still good for

As **fixtures** they are reasonable: plausible hostel knowledge bases, realistic guest phrasing
including Hinglish, and a sensible spread of in-KB and out-of-KB questions. They exercised the
eval harness end to end, which is genuinely worth something.

What they cannot do is answer the R0 gate. That gate is *"do the two owners, unprompted, ask when
they can have it?"* — a question about two people's reaction, which no amount of generated text
can stand in for.

## When the real sessions happen

Capture into this directory with `ALTSTAY_CAPTURE_DIR` set (see
[phase-3-validation.md](../phase-3-validation.md) §3), annotate per §3.4, and **delete these
files** — do not leave real and synthetic transcripts side by side. Cases in
`backend/src/test/resources/eval/concierge-eval.jsonl` whose `source` points here will need their
provenance re-pointed at the real lines; 12 of the 34 currently cite a transcript line they did
not come from.
