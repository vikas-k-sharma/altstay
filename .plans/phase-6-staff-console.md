# Phase 6 — The staff console

The interface for the roles Track B already built. Today `OWNER`, `MANAGER` and `FRONT_DESK` exist as
authorities, are enforced on one endpoint, and have **nowhere to log in to** — the frontend is a
knowledge-base editor and a guest chat window, both anonymous.

This phase is the front end of [phase-5-pms-core.md](phase-5-pms-core.md) and depends on it entirely.
It follows [phase-2-frontend.md](phase-2-frontend.md)'s conventions: Next.js 16 App Router, React 19,
Tailwind v4, zod contracts, and **the browser never calls Spring directly.**

---

## 0. The constraint that shapes every decision below

[phase-3-validation.md](phase-3-validation.md) §9.1 constraint 1:

> The R0 demo path is untouchable until the sessions are done.

So: **the console lives entirely under `/console`, and `/` does not change.** No shared layout, no
shared providers, no "while we're in here" refactor of `ConsoleShell`, no auth check that could
redirect an anonymous visitor away from the demo. The two applications share the repository, the
design tokens in `globals.css`, and nothing else until October.

The name collision is unfortunate and worth flagging before it causes a mistake:
`src/components/console/ConsoleShell.tsx` is the **existing anonymous demo's** split pane, not this
phase's console. New work goes under `src/components/staff/` and `src/app/console/`.

A test asserts `/` renders without a session. That is this phase's standing guard, the counterpart of
`ChatControllerTest.anonymousPostChatSucceedsWithNoCredentials` on the backend.

---

## 1. Authentication through the BFF — the load-bearing detail

Spring issues an httpOnly `JSESSIONID` with `SameSite=Strict` (Track B §3.3). The browser cannot use
it directly, because the browser never talks to Spring: it talks to Next on `:3000`, and Next talks
to Spring on `:8080`. A cookie set for one origin is not sent to the other.

**Design: the BFF relays the session.**

```
browser ──POST /api/console/login──► Next route handler ──POST /api/v1/auth/login──► Spring
                                            │                                          │
        ◄── Set-Cookie: altstay_session ────┘◄──────── Set-Cookie: JSESSIONID ─────────┘
             (httpOnly, SameSite=Strict, Path=/, Secure in production)

browser ──GET /api/console/bookings──► Next reads altstay_session ──► Spring, with Cookie: JSESSIONID
             (cookie sent automatically)          (server-side, never exposed to JS)
```

Four properties this must have, each of which is a test:

1. **The upstream `JSESSIONID` value never reaches client JavaScript.** The BFF re-emits it under its
   own cookie name, `httpOnly`, so it is not readable from the page and not visible in any client
   bundle.
2. **`BACKEND_URL` stays server-side.** No `NEXT_PUBLIC_` prefix, and a test greps the built client
   bundle for the backend origin.
3. **Logout clears both** — the BFF calls Spring's `/api/v1/auth/logout` *and* expires its own cookie.
   Clearing only one leaves a session that is alive on one side.
4. **`SameSite=Strict` on the BFF cookie**, asserted on the wire the way `AuthLoginIT` asserts
   Spring's. A servlet default and a framework default are both defaults nobody has checked.

**This is what keeps the CSRF decision sound.** `SecurityConfig` disables CSRF on `/api/v1/**`
because the browser never has a direct route to Spring
([phase-4-foundations.md](phase-4-foundations.md) §3.4, and `CLAUDE.md`). Nothing in this phase may
give it one — no `NEXT_PUBLIC_BACKEND_URL`, no direct `fetch` to `:8080` from a component, no CORS
relaxation. If that invariant is ever broken, the CSRF decision is void and has to be revisited
before anything ships. Mutations from the console pass through Next route handlers on the same
origin, which is where the CSRF boundary now sits.

---

## 2. Screens

Seven routes. The ordering is by whom they serve, front desk first — roadmap R3's metric is *"daily
active use by front-desk staff — not owners. Owners buy; staff decide whether it survives."*

### 2.1 `/console/login` — tenant-scoped sign-in

Three fields: **workspace**, email, password. The workspace slug is not optional and not a nicety;
V1 keys users on `unique (tenant_id, lower(email))`, so email alone is not a login identity
(§3.2). The field needs one line of helper text saying so, or every first-time user will ask.

A failed login shows one message for every failure mode, because the backend deliberately returns one
— `AuthLoginIT.inactiveAccountIsNotAnEnumerationOracle`. The UI must not helpfully distinguish
"unknown workspace" from "wrong password" and reintroduce the oracle at the top of the stack.

### 2.2 `/console` — today

The front desk's daily driver, and the screen that decides whether this survives contact with a
shift:

- **Arrivals today**, each with a one-click check-in.
- **Departures today**, each with a one-click check-out.
- **In-house** count and list.
- Occupancy for tonight: beds sold / beds available.
- Unpaid balances among today's arrivals.

"Today" is the **property's** local date, from `property.timezone` (phase-5 §2), never the browser's
and never UTC. This is where that field earns itself: a 6am arrivals list computed in UTC is empty in
India, and the bug reads as data loss.

### 2.3 `/console/calendar` — availability grid

Dates across, room types down, 14 days by default and up to 60. Each cell shows available units (or
available spaces for `WHOLE` room types) and the nightly rate. Clicking a cell starts a booking
pre-filled with that room type and date.

Fed by one call to `/api/v1/properties/{slug}/availability` — the sweep-line read path exists so this
screen is one request rather than one per cell.

**Drag-and-drop bed assignment is roadmap R3 and is not built here.** A grid you can read and click
is what a pilot needs; a grid you can drag is a project.

### 2.4 `/console/bookings` and `/console/bookings/[reference]`

List with filters that a front desk actually uses: date range, status, guest name, reference. Detail
shows the guest, the lines, the assigned beds, the money, the status history with who did what and
when, and the transition actions legal from the current status — the illegal ones are absent, not
disabled-with-a-tooltip.

### 2.5 `/console/bookings/new`

Dates and guest count → availability for those dates → pick a room type (and bed, for `PER_UNIT`) →
guest details → **quote** → confirm.

The quote comes from `POST /api/v1/bookings/quote`, which allocates nothing, so the price shown is
the price computed by the same code that will charge it — not a number the frontend adds up. The
confirm step sends an `Idempotency-Key` so a double-click produces one booking (phase-5 §5).

A `409 no-availability` between quote and confirm is an ordinary outcome, not an error state: the bed
went while the guest was deciding. It re-renders availability and says so plainly.

### 2.6 `/console/settings/*` — property, inventory, rates

`OWNER` and `MANAGER` only.

- **Property** — address, contacts, timezone, currency, check-in/out times, tax rate, amenities.
- **Room types** — code, name, sale mode, kind, occupancy, base rate.
- **Spaces and units** — a space with its bed list; capacity is shown as the derived count and is not
  editable, because it is not stored (phase-5 §3.2).
- **The hybrid mapping** — which room types a space can serve. This screen needs a sentence of
  explanation and an example, because it is the one concept in the product that has no equivalent in
  the software these owners have used before.
- **Rates** — pick a room type, a date range and an amount; the API expands it to per-date rows.

This is also the onboarding path. Roadmap §7 says R1 onboarding is white-glove and R2 is guided setup
in under thirty minutes; these screens are what makes the white-glove version repeatable rather than
a founder with a SQL client.

### 2.7 `/console/knowledge-base` — the persisted editor

Track C's API (phase-4-completion §1), with the version history alongside the editor: who changed
what, when, and the ability to read a previous version. Save is explicit, not on blur, and an
unchanged save is a visible no-op rather than a silent one.

**The anonymous editor at `/` is a different component and stays exactly as it is.** Two editors is
more code; an auth hiccup during an unrepeatable beta hour is worse.

---

## 3. Data flow

Route handlers under `src/app/api/console/…`, one per resource, each zod-validating **both**
directions — the request from the browser and the response from Spring. That bidirectional validation
is a Phase 2 convention and it earns its keep here: it is what turns a backend contract change into a
loud test failure instead of a blank screen.

Contracts live in `src/lib/contracts/` alongside the existing `contracts.ts`, one module per domain
(`booking`, `inventory`, `availability`, `property`). They mirror the backend records by hand; there
is no code generation, and the DoD requires a test that a sample response from each endpoint parses.

Server Components render everything that does not need interactivity — the lists, the calendar grid,
the booking detail. Client Components are the forms and the actions. Fetch on the server where the
session cookie is already available; there is no client-side data-fetching library and none is needed
at this size.

**Read `node_modules/next/dist/docs/` rather than writing Next 14/15 patterns from memory.** Next 16
diverges from most training data, and `frontend/AGENTS.md` is generated by `next dev` and must never
be hand-edited.

One inherited lesson that will recur: any `useSyncExternalStore` added here needs a
`getServerSnapshot` returning a **stable frozen reference**, and it must not read `localStorage`. A
fresh object per call is an infinite-loop warning; reading storage there is a hydration mismatch that
only appears after a user has saved something. `useKnowledgeBase` already paid for that lesson.

---

## 4. The UI is not the security boundary

The console hides what a role cannot do — a `FRONT_DESK` user does not see the Settings section.
**That is presentation.** The enforcement is `@PreAuthorize` on the server, and phase-5 §8 requires a
403 test for every cell in the matrix regardless of what the UI renders. Any review of this phase
that accepts "the button isn't there" as the answer to "can a front desk change rates?" has accepted
the wrong answer.

Route-level guards redirect an unauthenticated visitor to `/console/login` and an under-privileged one
to `/console` with a plain message. Neither is trusted for anything.

---

## 5. Error and empty states

- **The three model states stay distinct** (phase-4-completion §2.1): 429 "one moment", 503 "paused",
  502 "offline", 504 "timed out". They are already distinct in the API and must not be collapsed back
  into one string in the console the way `api.ts` currently collapses 502 and 503.
- **409 is a normal outcome on the booking path**, not a crash. Availability moved; say what happened
  and re-render.
- **Empty states are onboarding.** A property with no room types shows the way to create one, not an
  empty table. This is the first screen a new tenant sees after provisioning.
- **Nothing personal in the URL.** Guest names, emails and phone numbers stay out of query strings —
  they end up in server logs and browser history. Bookings are addressed by `reference`.

---

## 6. Testing

`vitest` + Testing Library, matching Phase 2. Nothing here needs a running backend.

| Test | What it proves |
| --- | --- |
| `/` renders with no session | §9.1 constraint 1 — the standing guard |
| Login route handler relays the cookie | The session actually works |
| The upstream cookie is not readable from the page | §1 property 1 |
| Logout clears both cookies | No half-dead session |
| `SameSite=Strict` and `httpOnly` on the BFF cookie, asserted on the response headers | Not a framework default nobody checked |
| Built client bundle contains no backend origin | `BACKEND_URL` stays server-side |
| Each contract module parses a recorded sample response | The hand-mirrored contracts are right |
| Booking form rejects `check_out <= check_in` before submitting | The commonest input error, caught early |
| A `409 no-availability` re-renders rather than throwing | The race is an outcome, not a bug |
| Role-gated navigation hides what the role cannot do | Presentation only — the server test is the real one |

---

## 7. Definition of Done

```powershell
cd frontend; npm run test; npm run build; npm run lint
```

```powershell
cd backend; .\mvnw.cmd clean verify
```

- [ ] `npm run test`, `npm run build` and `npm run lint` all pass
- [ ] **`/` is byte-identical in behaviour**: [dev-runbook.md](dev-runbook.md) §4 re-walked in full —
      live knowledge-base sync, escalation, preset switching, mobile layout, keyboard flow — and
      `git diff --stat` shows no change under `src/components/chat`, `src/components/admin`,
      `src/components/console`, `src/hooks/useKnowledgeBase.ts` or `src/lib/presets.ts`
- [ ] A test asserts `/` renders with no session
- [ ] `grep -r "NEXT_PUBLIC_BACKEND" frontend/src` returns nothing, and the built client bundle
      contains no `:8080` origin — the invariant `SecurityConfig`'s CSRF decision rests on
- [ ] Login → today → calendar → new booking → check in → check out works end to end against a
      provisioned tenant, in a browser, and the walkthrough is written into
      [dev-runbook.md](dev-runbook.md) §8
- [ ] A `FRONT_DESK` session gets **403** from the rates endpoint with the request issued directly to
      the BFF, not merely a hidden menu item
- [ ] The three throttle/outage states render as three different messages
- [ ] No guest name, email or phone appears in any URL
- [ ] Console renders usably at 1280×800 and at tablet width — a front desk is not on a phone, and
      roadmap §8 says the responsive web console *is* the staff app

Phase 6 is done when a front-desk shift could be run on it, and the anonymous demo has not noticed
that any of it exists.
