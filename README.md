# AltStay OS

A Property Management System for **alternative, hybrid-inventory stays** — hostels, surf camps,
retreat centres — entered through a lean wedge: an AI concierge that answers guest questions from
a knowledge base the property owner can edit live.

> **Status: Phase 2 of 3 (prototype).** Runs locally, single property, no database, no auth.
> Not deployable yet. See [Roadmap](#roadmap).

## Why this exists

Hotel PMS software assumes a room is the unit of sale. That breaks immediately for a 40-bed
hostel where the same physical room is a 6-bed dorm on Tuesday and a private double on Saturday,
which also sells a 7-day yoga retreat and rents scooters. Owners run the gaps in spreadsheets.

Second gap: in India and South-East Asia guests talk to properties on **WhatsApp**, before and
during their stay. No PMS lives there, so the owner is the integration — personally, at 2 AM.

The concierge is the wedge into that second gap. The inventory model is the actual product.
Full reasoning, release gates and what we deliberately won't build:
[`.plans/product-roadmap.md`](.plans/product-roadmap.md).

## Architecture

A monorepo of two decoupled applications. The browser never talks to the API directly — it goes
through a Next.js Route Handler acting as a backend-for-frontend.

```
Browser  ──POST /api/chat──▶  Next.js BFF  ──POST /api/v1/chat──▶  Spring Boot  ──▶  Gemini
         ◀──── JSON ────────  (zod in/out)  ◀──── JSON ──────────   (stateless)
```

The BFF exists so the API URL and credentials stay server-side, so CORS never enters the picture,
and so auth and rate limiting have one obvious home in R1.

**The API is stateless.** Every request carries its own knowledge base and full conversation
history. That's what makes "edit a rule on the right, see it reflected in the next message on the
left" work with no restart and no cache invalidation — which is the entire demo.

```
altstay/
├─ .plans/      Phase plans, code reviews, product roadmap, dev runbook
├─ backend/     Spring Boot 4.1.1 · Java 25 · Spring AI 2.0.1 · Gemini
└─ frontend/    Next.js 16 (App Router) · React 19 · TypeScript · Tailwind v4
```

Each app has its own README with detail: [backend](backend/README.md) · [frontend](frontend/README.md).

## Quick start

Requires **JDK 25** and **Node 24+**. Two terminals.

```powershell
cd backend; .\mvnw.cmd spring-boot:run
```

```powershell
cd frontend; npm install; npm run dev
```

Open <http://localhost:3000>.

First run on a fresh machine needs environment setup (`JAVA_HOME`, PATH) — and the verification
steps are worth doing in order, because they tell you *which layer* broke:
**[`.plans/dev-runbook.md`](.plans/dev-runbook.md)**.

## The demo

1. In the right pane, change check-in from `2:00 PM` to `12:00 PM`. Don't reload.
2. In the left pane ask *"what time is check-in?"* → the answer says 12 PM.
3. Ask something absent from the rules → it escalates to a human instead of inventing an answer.

Step 3 is the one that matters commercially. A concierge that confidently invents a pet policy is
worse than no concierge, and it's the objection that blocks the sale.

## Tests

```powershell
cd backend; .\mvnw.cmd clean verify
```

```powershell
cd frontend; npm run test; npm run build; npm run lint
```

Both suites run offline with no API key. The backend's live Gemini test is opt-in:

```powershell
$env:ALTSTAY_LIVE_TESTS="true"; .\mvnw.cmd verify
```

## Roadmap

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | Backend — stateless `POST /api/v1/chat` | delivered |
| 2 | Frontend — split-pane console | delivered |
| 3 | Guardrail tuning, edge cases, beta-tester sessions | next |
| R1+ | Multi-tenancy, WhatsApp Cloud API, inventory, bookings | gated on Phase 3 evidence |

Phase 3 is not a coding phase. It's two hostel owners trying to break the AI, and **capturing
every question they ask verbatim** — that transcript is the prompt-tuning input, the future
regression suite, and the evidence for whether R1 is worth starting.

## Known limitations

Deliberate for a prototype, blocking for a deploy:

- **No auth or rate limiting.** Both endpoints are open and cost money per call.
- **Conversation history is client-supplied and trusted**, so a caller can fabricate assistant
  turns and bypass the prompt guardrails.
- **No timeout on the model call.** The BFF aborts at 25s; the backend thread stays parked.
- Single property, no database, knowledge base lives in the browser's `localStorage`.
- The Gemini API key is currently hardcoded in `application.yaml` for local development.

Tracked in [`.plans/phase-1-review.md`](.plans/phase-1-review.md) and
[`.plans/phase-2-review.md`](.plans/phase-2-review.md).

## Documentation

| Document | Contents |
| --- | --- |
| [`.plans/README.md`](.plans/README.md) | Index of all planning docs |
| [`.plans/product-roadmap.md`](.plans/product-roadmap.md) | Thesis, value ladder, R0–R4 gates, irreversible architecture decisions |
| [`.plans/dev-runbook.md`](.plans/dev-runbook.md) | Local setup and layer-by-layer verification |
| [`.plans/phase-1-backend-ai.md`](.plans/phase-1-backend-ai.md) | Backend plan — **§4 is the canonical API contract** |
| [`.plans/phase-2-frontend.md`](.plans/phase-2-frontend.md) | Frontend plan |
