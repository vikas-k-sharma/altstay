# AltStay OS — Plans

Phase-by-phase implementation plans. One file per phase. Only the **current** phase is
written in detail; future phases stay coarse until we get there.

## Product in one line

A Property Management System for *alternative, hybrid-inventory* stays (hostels,
experiential retreats) — entered through a lean wedge: an AI concierge that answers guest
questions from a property-specific knowledge base the owner can edit live.

Strategy, market, and release gates: **[product-roadmap.md](product-roadmap.md)**.

## Architecture

Monorepo, two decoupled apps, REST between them.

```
altstay/
├─ .plans/        ← you are here
├─ backend/       Spring Boot 4.1.1 · Java 25 · Spring AI 2.0.1 · Google GenAI (Gemini)
└─ frontend/      Next.js 16 (App Router) · React 19 · TypeScript · Tailwind v4
```

## Documents

| Document | What it's for |
| --- | --- |
| [product-roadmap.md](product-roadmap.md) | Thesis, value ladder, R0–R4 releases with metrics and kill criteria. Also holds the irreversible architecture calls: multi-tenancy + datastores (§4), hybrid inventory and its concurrency boundary (§5), Form C vision pipeline (§6), and what we won't build (§8) |
| [phase-1-backend-ai.md](phase-1-backend-ai.md) | Phase 1 implementation plan (the API contract in §4 is the frontend's source of truth) |
| [phase-1-review.md](phase-1-review.md) | Review of the delivered Phase 1 backend |
| [phase-2-frontend.md](phase-2-frontend.md) | Phase 2 implementation plan |
| [phase-2-review.md](phase-2-review.md) | Review of the delivered Phase 2 frontend |
| [phase-3-validation.md](phase-3-validation.md) | Phase 3 plan — timeout, guardrail battery, beta capture, and R0 gate |
| [phase-3-review.md](phase-3-review.md) | Phase 3 review — **§0 is a correction; §1–§7 overstate what was verified** |
| [phase-4-foundations.md](phase-4-foundations.md) | Phase 4 plan — gate-independent R1 foundations. §1 records the Postgres setup and its privilege checks (**the default Neon role failed one**); §2.4 onward is the executable plan for tenant binding, auth, KB persistence, rate limiting and ops, each with its own definition of done |
| [phase-4-completion.md](phase-4-completion.md) | Finishes Phase 4 — Tracks C (KB → Postgres), D (rate limiting) and E (ops), **re-specified against the repo as Tracks A and B left it**. §0 is the delta table: what §4–§6 assumed and what is actually true now |
| [phase-5-pms-core.md](phase-5-pms-core.md) | Phase 5 plan — the PMS itself. Property expansion, room types + bed-level units, the concurrency boundary from roadmap §5, booking lifecycle, rates, availability, and repeatable tenant provisioning. §0 is the KILL-survival argument that admits it |
| [phase-6-staff-console.md](phase-6-staff-console.md) | Phase 6 plan — the staff console for `OWNER`/`MANAGER`/`FRONT_DESK`. §0 is the rule that keeps `/` untouched; §1 is the BFF session relay the CSRF decision depends on |
| [dev-runbook.md](dev-runbook.md) | **Start here to run the stack locally** — setup, both servers, layer-by-layer verification |

## Prototype phases

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | Backend — stateless `POST /api/v1/chat` | delivered |
| 2 | Frontend — split-pane Admin Panel + WhatsApp-style chat | delivered |
| 3 | Guardrails, edge cases, beta-tester sessions, model timeouts, eval battery | **engineering delivered and green; sessions moved to October 2026.** Gate unasked — [review §0](phase-3-review.md) |
| 4 | R1 foundations — Postgres, multi-tenancy + RLS, auth, KB persistence | **delivered and reviewed; two DoD items still open.** Track A (Postgres RLS, V1–V5, `TenantIsolationIT` 6/6, `TenantBindingIT` 5/5). Track B (auth login, three roles, `AuthLoginIT` 8/8). Track C (KB versioning, retry, `KnowledgeBaseIsolationIT` 7/7). Track D (429/503 split, 3-tier token-bucket limiter, turn persistence, roadmap §9 metric 5). Track E (structured logging, correlation-id filter, DB health indicator). **84 unit + 29 IT green; offline invariant holds with `.env.properties` present and moved aside.** The 2026-08-29 review is [phase-4-completion.md §6](phase-4-completion.md) — 10 findings, all fixed, each watched failing first. **Deferred, not blocking:** the live-200 runbook walk, which needs a billed key — a model switch to `gemini-2.5-flash-lite` was tried and the 20/day limit is per model, so it buys one more bucket of 20, not headroom |
| 5 | The PMS core — property, room types + bed-level units, allocation, bookings, rates, provisioning | **planned** — [phase-5-pms-core.md](phase-5-pms-core.md). Not started |
| 6 | Staff console — front desk, calendar, bookings, setup | **planned** — [phase-6-staff-console.md](phase-6-staff-console.md). Depends on Phase 5 |
| R1+ | Production SaaS — see [product-roadmap.md](product-roadmap.md) | **still gated** — the R0 gate is undecided, not passed |

> **Phases 3 and 4 run in parallel, deliberately.** Phase 3's remaining deliverable is two owner
> sessions, now scheduled for October. Phase 4 builds only what survives a **KILL** verdict on that
> gate — see [phase-3-validation.md §9.1](phase-3-validation.md) for the admission rule and the
> three constraints that come with it. This is a documented override of the "one phase at a time"
> working agreement, not drift.

> **Phases 5 and 6 are admitted under the same rule, and the case is stronger.** If the gate kills,
> the roadmap's pivot is *"owner-facing tooling (the ops side)"* — a property record, inventory,
> bookings and a front-desk console **are** that pivot. What stays withheld is unchanged: the
> concierge answering availability from this inventory, WhatsApp webhooks, human handoff, and
> guest-thread semantics. See [phase-5-pms-core.md](phase-5-pms-core.md) §0.

## Open P0s

**One open.** Detail in [phase-3-review.md](phase-3-review.md) §0.

1. **The R0 gate was never asked.** The gate is *"do the two owners, unprompted, ask when they can
   have it?"* — no owner reaction is recorded anywhere. The files in `phase-3-transcripts/` are
   **synthetic fixtures**: future-dated, unannotated, and carrying a `seq` field the capture code
   never writes. **R1 must not start on this evidence.** Sessions scheduled for October 2026;
   the gate stays open until they happen. Phase 4's foundations do not close it and are not
   evidence for it.

2. **One session-critical item left, due before October** — the other three are fixed (see
   [phase-4-foundations.md](phase-4-foundations.md) §7). **`GOOGLE_API_KEY` is on the Gemini free
   tier at 20 requests PER DAY per model**, not the 5-per-minute limit this entry used to claim —
   measured 2026-08-29 from the 429 body, `GenerateRequestsPerDayPerProjectPerModel-FreeTier`,
   `quotaValue: 20`. Once spent, every question for the rest of the day renders to the owner as
   *"The concierge is paused right now."* (Track D now tells that apart from a real outage; before
   it, both read as *"offline"*.) **A single beta session asks more than 20 questions, so an October
   session on this key fails outright rather than stuttering.** Changing `ALTSTAY_MODEL` does not
   help: the quota is per project **per model**, so each new model name is one more bucket of 20 —
   `gemini-2.5-flash-lite` and `gemini-2.5-flash` were both measured at 20 and both exhausted the
   same day. **Needs a billed key.**

~~2. `mvnw clean verify` is RED~~ — resolved. The google-genai SDK's built-in `RetryInterceptor`
(default `attempts=5`, jittered backoff) was multiplying the read timeout; `HttpOptions.retryOptions`
now pins it to 1. A configured 2s timeout produces a 502 at 2104ms. `clean verify` is green:
15 unit tests + `ModelTimeoutIT`, offline, no key.

Closed:

1. ~~Hardcoded API key~~ — now `${GOOGLE_API_KEY}` with no default.
2. ~~`ChatLiveIT` never executes~~ — `maven-failsafe-plugin` configured; runs on `ALTSTAY_LIVE_TESTS=true`.
3. ~~`ApiApplicationTests` depends on key default~~ — `src/test/resources/application.yaml` added.
4. ~~Model call parks a thread~~ — the `CompletableFuture.supplyAsync` common-pool leak is gone;
   the call is now synchronous on the request's virtual thread with transport-level timeouts. The
   *duration* is still wrong (item 1 above), but the thread leak is genuinely fixed.
5. ~~No capture or eval harness~~ — `ALTSTAY_CAPTURE_DIR` JSONL capture and `EvalCorpusTest`
   (offline, 34 cases) both land and work.

## Working agreement

- Do not build Phase N+1 scaffolding "while we're in here." Each phase ships something
  runnable and demoable on its own.
- Every phase plan ends with a **Definition of Done** that is verifiable by running commands,
  not by reading code.
- Releases R1+ have **kill criteria**, not just goals. Check them honestly.
