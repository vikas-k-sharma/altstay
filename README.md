# AltStay OS

A Property Management System for **alternative, hybrid-inventory stays** — hostels, surf camps,
retreat centres — entered through a lean wedge: an AI concierge that answers guest questions from
a knowledge base the property owner edits live.

> **Status:** PMS core delivered. Multi-tenant PostgreSQL with row-level security, tenant-scoped
> authentication, and the inventory/rates/bookings model are built and tested. The guest-facing
> concierge runs single-property and unauthenticated by design while the product question it
> exists to answer is still open — see [Roadmap](#roadmap).

## Why this exists

Hotel PMS software assumes a room is the unit of sale. That breaks immediately for a 40-bed
hostel where the same physical room is a 6-bed dorm on Tuesday and a private double on Saturday,
which also sells a 7-day yoga retreat and rents scooters. Owners run the gaps in spreadsheets.

Second gap: in India and South-East Asia guests talk to properties on **WhatsApp**, before and
during their stay. No PMS lives there, so the owner is the integration — personally, at 2 AM.

The concierge is the wedge into that second gap. **The inventory model is the actual product.**

## What's built

**Tenancy and access**

- Multi-tenant PostgreSQL. Isolation is enforced by **row-level security in the database**, not by
  application `WHERE` clauses alone.
- The tenant is bound to the connection *inside* the transaction
  (`set_config('app.tenant_id', …, true)`) and is only ever derived from an authenticated
  principal — never from a client-supplied value.
- Tenant-scoped login (property slug + email + password) on an httpOnly, `SameSite=Strict` session
  cookie. Roles: `OWNER`, `MANAGER`, `FRONT_DESK`.
- Token-bucket rate limiting, per tenant and per anonymous caller.

**Inventory, rates and bookings**

- Room types, physical spaces, and the dorm-bed / private-room duality that makes hostels awkward.
- Night-by-night availability from sorted event deltas, verified against an independent
  brute-force oracle in a property-based test.
- Rate plans with a date calendar, quote calculation, bookings with a status machine, and
  allocation guarded by a PostgreSQL exclusion constraint so two guests cannot hold one bed.

**Concierge**

- Stateless `POST /api/v1/chat`. Every request carries its own knowledge base and history, which is
  why editing a rule lands on the next message with no restart and no cache invalidation.
- Refuses to answer outside its knowledge base, and escalates to a human instead of inventing.

**Schema** — 11 Flyway migrations (`V1`–`V11`), applied at startup.

## Architecture

A monorepo of two decoupled applications. The browser never talks to the API directly — it goes
through a Next.js Route Handler acting as a backend-for-frontend.

```
Browser  ──POST /api/chat──▶  Next.js BFF  ──POST /api/v1/chat──▶  Spring Boot  ──▶  Gemini
         ◀──── JSON ────────  (zod in/out)  ◀──── JSON ──────────      │
                                                                  PostgreSQL
                                                                  (RLS per tenant)
```

The BFF exists so the API URL and credentials stay server-side, so CORS never enters the picture,
and so auth, rate limiting, and session capture have one clean boundary.

```
altstay/
├─ backend/     Spring Boot 4.1.1 · Java 25 · Spring AI 2.0.1 · PostgreSQL · Flyway
└─ frontend/    Next.js 16 (App Router) · React 19 · TypeScript · Tailwind v4
```

Each app has its own README: [backend](backend/README.md) · [frontend](frontend/README.md).

## Engineering notes

The decisions here that were not obvious, and what they cost to find:

- **The database's default role bypassed every RLS policy.** The managed provider's default owner
  role has `rolbypassrls = true`, so an app connecting as it would have had a tenancy model that
  enforced nothing — while the isolation tests still passed. The app connects as a purpose-made
  `altstay_app` role created with plain SQL, because roles created through the provider's console
  inherit a superuser group. The tenancy tests were watched failing with RLS disabled before being
  trusted.

- **Connection pooling and tenancy interact badly.** Binding a tenant with `SET LOCAL` ties it to a
  connection; a transaction-mode pooler multiplexes sessions across backends, which is a route for
  one tenant's binding to land in another tenant's query. The app uses the direct host.

- **A JPA `save()` does not reach the database until flush**, so a constraint-backed booking race
  surfaced at *commit* — outside the service, as a 500 with a Postgres constraint name in the body.
  Allocations are written with `saveAndFlush` so the violation happens where it can be translated
  into a 409 a human can act on.

- **The model SDK installs its own retry interceptor unconditionally**, defaulting to five attempts
  with exponential backoff — silently multiplying a 2s read timeout into 13–21s of real latency.
  It is now pinned to a single attempt at the HTTP client layer. Logging the *configured* timeout
  rather than the *elapsed* one hid this for an entire phase.

- **The test `application.yaml` replaces the main one rather than merging with it**, so any setting
  that lives only in the main file is covered by no test. That shipped a missing `SameSite` cookie
  attribute which read as configured. Config that must hold in production *and* be testable now
  lives in a bean.

- **A randomized test is worth exactly as much as the independence of its oracle.** An early
  availability property test compared the implementation against a copy of its own loop: 250
  iterations incapable of failing. The oracle now materializes occupancy night by night where the
  implementation carries sorted event deltas.

## Quick start

Requires **JDK 25** and **Node 24+**. Two terminals.

```powershell
cd backend; .\mvnw.cmd spring-boot:run
```

```powershell
cd frontend; npm install; npm run dev
```

Open <http://localhost:3000>.

`GOOGLE_API_KEY` has no default and the app fails fast without it. The database credentials
(`ALTSTAY_DB_URL`, `ALTSTAY_DB_USER`, `ALTSTAY_DB_PASSWORD`) are the same, and the URL must carry
`sslmode=require`.

## Deployment

Live on Azure Container Apps at
<https://altstay-web.yellowriver-ae1bc796.southeastasia.azurecontainerapps.io>. The Next.js app has
public ingress; Spring has **internal-only** ingress, so the browser has no route to it at all —
which is what makes phase-4 §3.4's "the browser never calls Spring directly" a property of the
network rather than a convention.

`git push` to `main` is the whole deployment process: GitHub Actions runs both offline suites,
builds two container images to ghcr.io, and rolls both revisions.

The full procedure — rebuild-from-nothing steps, cost, and the two commands that switch between
$0/month and always-warm — is in **[docs/deploy-azure.md](docs/deploy-azure.md)**.

## The demo

1. In the right pane, change check-in from `2:00 PM` to `12:00 PM`. Don't reload.
2. In the left pane ask *"what time is check-in?"* → the answer says 12 PM.
3. Ask something absent from the rules → it escalates to a human instead of inventing an answer.

Step 3 is the one that matters commercially. A concierge that confidently invents a pet policy is
worse than no concierge, and it is the objection that blocks the sale.

## Tests

```powershell
cd backend; .\mvnw.cmd clean verify
```

```powershell
cd frontend; npm run test; npm run build; npm run lint
```

**The suite runs offline.** With no API key and no database configured, the unit tests and the
offline integration tests run and the database-backed ones skip — the test configuration excludes
the DataSource, Hibernate and Flyway autoconfigurations to keep it that way.

Database integration tests are opt-in, against real PostgreSQL 13+:

```powershell
$env:ALTSTAY_DB_TESTS="true"; .\mvnw.cmd verify
```

They cannot run on H2 — RLS does not exist there, so an H2 suite would prove tenancy works in a
database this project does not ship.

The live model eval battery is opt-in separately via `ALTSTAY_LIVE_TESTS=true`.

## Roadmap

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | Stateless `POST /api/v1/chat` | delivered |
| 2 | Split-pane console | delivered |
| 3 | Guardrail tuning, model timeouts, eval battery | delivered |
| 4 | PostgreSQL, multi-tenancy + RLS, authentication, rate limiting | delivered |
| 5 | PMS core — inventory, availability, rates, bookings, allocation | delivered |
| 6 | Staff console over the PMS API | in progress |
| — | WhatsApp Cloud API, human handoff, guest threads | gated |

The gated items wait on one question no test suite can answer: whether hostel owners, shown the
concierge unprompted, ask when they can have it. Until that happens the concierge stays
deliberately narrow, and the inventory model is where the effort goes.

## Known limitations

Deliberate, and tracked:

- **`POST /api/v1/chat` and `/actuator/health` are intentionally unauthenticated.** Everything else
  under `/api/v1/**` requires a session. A standing test asserts the chat endpoint stays open, so
  closing it has to be a deliberate act rather than an accident.
- **Conversation history is client-supplied and trusted**, so a caller can fabricate assistant
  turns. A server-side session store is the fix, and is not built yet.
- The concierge is **single-property**, and its knowledge base lives in the browser's
  `localStorage` rather than in the multi-tenant schema beside everything else.
- CSRF is disabled on `/api/v1/**` only, resting on the invariant that the browser reaches Spring
  exclusively through the BFF, server-to-server. If a browser ever gains a direct route, that
  decision is void.
