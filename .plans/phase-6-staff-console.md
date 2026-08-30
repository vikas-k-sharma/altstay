# Phase 6 — The staff console

The interface for the roles Track B built. `OWNER`, `MANAGER` and `FRONT_DESK` exist as authorities,
are enforced across twelve controllers, and have **nowhere to log in to** — the frontend is still a
knowledge-base editor and a guest chat window, both anonymous.

This is the front end of [phase-5-pms-core.md](phase-5-pms-core.md), which is now delivered. It
follows [phase-2-frontend.md](phase-2-frontend.md)'s conventions: Next.js 16 App Router, React 19,
Tailwind v4, zod contracts, and **the browser never calls Spring directly**.

**Every endpoint this plan consumes exists and is verified** (§1). Nothing here is written against
an interface that has yet to be built.

---

## 0. The constraint that shapes every decision below

[phase-3-validation.md](phase-3-validation.md) §9.1 constraint 1:

> The R0 demo path is untouchable until the sessions are done.

So: **the console lives entirely under `/console`**, with no shared layout, no shared providers, no
"while we're in here" refactor of the existing components, and no auth check that could redirect an
anonymous visitor away from the demo.

### 0.1 The demo moves to `/concierge` — decided 2026-08-30

`/` is currently the concierge demo itself, which is not a presentable front door for anything but a
beta session. **The demo moves to `/concierge`, and a marketing site takes `/` in Phase 7.**

This is a change to the demo path, so it is a deliberate, recorded narrowing of §9.1 constraint 1
rather than a quiet edit. What the constraint actually protects is the *experience* the two October
owners walk through — that it exists, needs no login, and behaves exactly as it does today. A URL
move preserves all three, provided it is done in the one shape that cannot break the runbook:

| Route | Phase 6 | Phase 7 |
| --- | --- | --- |
| `/concierge` | The demo, **byte-identical** — same `ConsoleShell`, same `localStorage`, same presets | unchanged |
| `/` | **Redirects to `/concierge`** | The marketing landing page |

**The redirect is what makes this safe.** `dev-runbook.md` §4 says "open http://localhost:3000", and
with the redirect in place that instruction stays true — a beta session run from muscle memory lands
on the demo either way. The runbook is updated to name `/concierge` directly, but nothing breaks if
someone follows the old version.

What moves and what does not:

- **Moves:** `src/app/page.tsx` becomes a redirect; a new `src/app/concierge/page.tsx` renders
  `ConsoleShell` — the same two lines the old page held.
- **Does not move:** every component, hook and lib the demo is made of. `src/components/chat`,
  `src/components/admin`, `src/components/console`, `src/hooks/`, `src/lib/presets.ts` and
  `src/lib/api.ts` are untouched. The demo is relocated, not refactored.

### 0.2 Two traps, both worth naming before they cause a mistake

1. **The name collision.** `src/components/console/ConsoleShell.tsx` is the **existing anonymous
   demo's** split pane, not this phase's console. New work goes under `src/components/staff/` and
   `src/app/console/`. Renaming the old one is exactly the "while we're in here" change that is
   forbidden until October — and it is now *more* tempting, because `/console` and `ConsoleShell`
   will sit next to each other in the tree meaning two different things. Leave it.
2. **`globals.css` is shared.** §8 adds tokens to it. **Additive only** — no existing token's value
   changes, `--background` and `--foreground` are left exactly as they are, and the runbook §4
   re-walk is what proves the demo still looks right.

**The standing guard moves with the demo.** A test asserts `/concierge` renders without a session,
and a second asserts `/` redirects there. Together they are this phase's counterpart of
`ChatControllerTest.anonymousPostChatSucceedsWithNoCredentials` — and the pair must exist before the
move is considered done, or the guarantee has been deleted rather than relocated.

---

## 1. What this phase builds on — verified 2026-08-30

### 1.1 The verification, by running the commands

| Command | Result |
| --- | --- |
| `.\mvnw.cmd clean verify` (offline; `ALTSTAY_DB_TESTS` unset) | **177 unit tests, 0 failures, 0 skipped**; 52 of 53 integration tests correctly skipped, `ModelTimeoutIT` runs |
| `.\mvnw.cmd clean verify` with `ALTSTAY_DB_TESTS=true` | **177 unit + 50 integration green** against Neon; only `ChatLiveIT` (2) and `ConciergeEvalIT` (1) skip, as designed |
| `npm run test` | **23 tests, 3 files, passing** |

The Phase 5 integration tests that matter to this phase, all green:
`AllocationConstraintIT` 11 · `BookingConcurrencyIT` 1 · `BookingLifecycleIT` 2 · `SchemaTenancyIT` 3
· `InventoryIntegrityIT` 3 · `TenantProvisioningIT` 1 · `KnowledgeBaseIsolationIT` 7 ·
`TenantIsolationIT` 6 · `TenantBindingIT` 5 · `AuthLoginIT` 8 · `ConversationPersistenceIT` 2.

Two claims were checked by reading the tests rather than trusting the counts, because both are the
kind that can pass without proving anything:

- **`BookingConcurrencyIT` genuinely races.** Eight threads on a fixed pool, held on a
  `CountDownLatch` start gate and released together, asserting exactly one success, seven failures
  each unwrapped to `BookingConflictException`/`NoAvailabilityException`, and exactly one active
  allocation row afterwards.
- **The red-first evidence is a live test, not a pasted log.** `AllocationConstraintIT` drops
  `allocation_no_overlap` inside a transaction, writes the same overlapping pair that failed moments
  earlier, asserts both rows land, and rolls back. It re-proves the constraint's necessity on every
  run, which is stronger than a recorded output.
- **`AvailabilityCalculatorPropertyTest`'s oracle is independent.** It materializes occupancy
  day-by-day rather than re-running the sweep line, across 250 seeded cases with the seed printed on
  failure — the review found and fixed an earlier version whose "oracle" was a copy of the
  implementation and therefore proved nothing.

### 1.2 The endpoints this console consumes

Read off the controllers, not the plan.

| Endpoint | Method | Role | Response |
| --- | --- | --- | --- |
| `/api/v1/auth/login` · `/logout` · `/me` | POST · POST · GET | — · — · session | `AuthUserResponse` |
| `/api/v1/properties` | GET · POST | session · OWNER | `PropertyResponse[]` |
| `/api/v1/properties/{slug}` | GET · PUT | session · OWNER | `PropertyResponse` |
| `/api/v1/amenities` | GET | session | `AmenityResponse[]` |
| `/api/v1/properties/{slug}/room-types` | GET · POST | session · MANAGER+ | `RoomTypeDto[]` |
| `/api/v1/properties/{slug}/room-types/{id}` | GET · PUT | session · MANAGER+ | `RoomTypeDto` |
| `/api/v1/room-types/{id}/spaces/{spaceId}` | POST · DELETE | MANAGER+ | 204 |
| `/api/v1/properties/{slug}/spaces` · `/{id}` | GET · POST · PUT | session · MANAGER+ | `SpaceDto` (units nested) |
| `/api/v1/properties/{slug}/availability` | GET | all three | `PropertyAvailabilityResponse` |
| `/api/v1/properties/{slug}/front-desk` | GET | all three | `FrontDeskResponse` |
| `/api/v1/properties/{slug}/rate-plans` | GET · POST | all three · MANAGER+ | `RatePlanDto` |
| `/api/v1/rate-plans/{id}/calendar` | GET · PUT | all three · MANAGER+ | `RateCalendarDto[]` · 204 |
| `/api/v1/bookings/quote` | POST | all three | `QuoteResponse` |
| `/api/v1/bookings` | GET · POST | all three | `BookingResponse[]` · `BookingResponse` |
| `/api/v1/bookings/{reference}` | GET · PATCH | all three | `BookingResponse` |
| `/api/v1/bookings/{reference}/transitions` | POST | all three | `BookingResponse` |
| `/api/v1/guests` · `/{id}` | GET · POST · PUT | all three | `GuestDto` |
| `/api/v1/properties/{propertyId}/knowledge-base` · `/history` | GET · POST · GET | session | `KnowledgeBaseVersionResponse` |

**Nothing is blocked.** All seven build slices in §12 can start.

### 1.3 Three gaps in the API found while writing this, and what the console does about them

None of these is a defect; each is a boundary the UI has to respect rather than paper over.

1. **`FrontDeskResponse` carries no occupancy figure.** It is `propertyId`, `propertySlug`, `date`,
   and three `BookingResponse[]` lists — arrivals, departures, inHouse. Tonight's occupancy is
   therefore a **second call**, to `availability` for a one-night range, not a field. §4.2 does that
   rather than counting bookings client-side, which would get the answer wrong the moment a booking
   holds more than one bed.
2. **`BookingResponse.allocations` covers current lines only.** Allocations are gathered from lines
   where `superseded_at is null` (V11). So a bed released by a **cancellation** appears with its
   `releasedAt` set, but the beds a guest held before a **date modification** do not — that line is
   superseded and drops out. The detail screen shows what it is given and does not claim to be a
   complete bed history. *Trigger to add one:* the first time someone asks "which bed were they in
   last week?" and the answer is not on the screen.
3. **Booking search has no guest-name filter.** `listBookings` takes `propertyId`, `status`, `from`,
   `to`, `guestId` and `reference` — six server-side filters, composing, with the date filter an
   overlap test rather than equality. Name search means resolving a guest through `/api/v1/guests`
   first. §4.4 does exactly that, in two steps, rather than filtering a page client-side and calling
   it search.

---

## 2. Authentication through the BFF — the load-bearing detail

Spring issues an httpOnly `JSESSIONID` with `SameSite=Strict` (Track B §3.3). The browser cannot use
it directly, because the browser never talks to Spring: it talks to Next on `:3000`, and Next talks
to Spring on `:8080`. A cookie set for one origin is not sent to the other.

### 2.1 The relay

```
browser ──POST /api/console/login──► Next route handler ──POST /api/v1/auth/login──► Spring
                                            │                                          │
        ◄── Set-Cookie: altstay_session ────┘◄──────── Set-Cookie: JSESSIONID ─────────┘

browser ──GET /console/bookings──► Next server component ──► Spring, with Cookie: JSESSIONID=…
             (cookie sent automatically)      (server-side; never exposed to page JS)
```

The BFF stores the **upstream cookie value** under its own name. It is not a new session, not a
JWT, and not a token the console mints — it is Spring's session id, held on the server side of the
BFF boundary and never handed to page JavaScript.

### 2.2 The cookie contract

| Attribute | Value | Why |
| --- | --- | --- |
| Name | `altstay_session` | Distinct from `JSESSIONID` so the two are never confused in a devtools panel |
| `httpOnly` | true | Page JavaScript must not be able to read the upstream session id |
| `sameSite` | `strict` | The CSRF boundary now sits at the BFF; see §2.5 |
| `path` | `/` | The console spans several route groups |
| `secure` | `process.env.NODE_ENV === 'production'` | `localhost` development is plain HTTP |
| `maxAge` | unset — a session cookie | It expires with the browser session, matching Spring's |

Asserted **on the response headers** in a test, not assumed from a framework default. A default
nobody has checked is exactly how `SameSite` went missing on the Spring side for a whole phase
(phase-4-foundations §3.7 finding 3).

### 2.3 Where the session is read

One server-only module, `src/lib/server/session.ts`, starting with `import 'server-only'` so that
importing it from a Client Component is a build error rather than a runtime leak:

```
getSession()         → { cookieHeader, user } | null   reads the cookie, calls /api/v1/auth/me
requireSession()     → session                          redirects to /console/login?next=… if absent
requireRole(...)     → session                          redirects to /console if the role is missing
upstream(path, init) → Response                         attaches Cookie: JSESSIONID=… and BACKEND_URL
```

`upstream()` is the **only** place `BACKEND_URL` is read. Every server component and every route
handler goes through it, so there is one function to audit rather than thirty call sites.

`/me` returns `AuthUserResponse` — `userId`, `tenantId`, `tenantSlug`, `email`, `fullName`,
`roles`. Note that `roles` carries **unprefixed** names (`OWNER`, not `ROLE_OWNER`); the `ROLE_`
prefix is added by `TenantUserDetails.getAuthorities()` for Spring's benefit and never appears on
the wire. The console compares against the bare names.

### 2.4 Expiry, logout, and the 401 path

- **Any upstream 401** means the session died. The BFF clears `altstay_session` and returns 401; the
  client redirects to `/console/login?next=<current path>`. A dead session must never render as an
  empty list — that is the same failure mode phase-4-foundations §2.4 describes for an unbound
  tenant, one layer up.
- **`missing-tenant` is also a 401.** `MissingTenantException` maps to 401 with that problem type;
  the console treats it identically to an expired session, because from the user's side it is one.
- **Logout clears both sides**: call Spring's `/api/v1/auth/logout`, *then* expire the BFF cookie.
  Clearing only the BFF cookie leaves a live session on Spring; clearing only Spring's leaves the
  console believing it is logged in.
- **Login while already logged in** replaces the cookie rather than stacking.

### 2.5 What must never happen

`SecurityConfig` disables CSRF on `/api/v1/**` **because** the browser has no direct route to Spring
(phase-4-foundations §3.4, and `CLAUDE.md`). That is a load-bearing invariant, not a convention.
Nothing in this phase may weaken it:

- No `NEXT_PUBLIC_BACKEND_URL`, ever.
- No `fetch` to `:8080` from any Client Component.
- No CORS relaxation to make one "just work."
- Every console mutation goes through a Next route handler on the same origin.

If a browser ever gains a direct route to Spring, the CSRF decision is void and has to be revisited
before anything ships. §13 has a search that fails the build if it happens.

---

## 3. Route map

| Route | Rendering | Role | Data source |
| --- | --- | --- | --- |
| `/` | Server | — | **Redirect to `/concierge`** until Phase 7 takes it (§0.1) |
| `/concierge` | Client | — | The existing demo, moved and otherwise untouched (§0.1) |
| `/console/login` | Client | — | `POST /api/console/login` |
| `/console` | Server | any | `front-desk` + a one-night `availability` call |
| `/console/calendar` | Server shell + Client grid | any | `availability` |
| `/console/bookings` | Server | any | `GET /api/v1/bookings` (six filters) |
| `/console/bookings/new` | Client | any | `availability` + `quote` + `POST /bookings` |
| `/console/bookings/[reference]` | Server + Client actions | any | `GET /api/v1/bookings/{ref}` |
| `/console/guests` · `/guests/[id]` | Server | any | `GET /api/v1/guests` |
| `/console/settings/property` | Server + Client form | OWNER | `GET/PUT /api/v1/properties/{slug}` |
| `/console/settings/inventory` | Server + Client editors | MANAGER+ | room-types, spaces, mapping |
| `/console/settings/rates` | Client | MANAGER+ | rate-plans + calendar |
| `/console/knowledge-base` | Client | MANAGER+ | KB endpoints |

Server Components render everything that does not need interactivity — lists, the booking detail,
the calendar shell. Client Components are the forms, the actions, and the grid's interactions. Data
is fetched on the server where the session cookie is already in hand. **There is no client-side
data-fetching library and none is needed at this size**; adding one is the resume-driven-architecture
smell roadmap §10 warns about.

### 3.1 Which property

A tenant may run more than one property (`property` is `unique (tenant_id, slug)` and
`listProperties()` returns a list), so every screen below is scoped to one.

**Decision: a property switcher in the console header, persisted to an `altstay_property` cookie,
with a single-property fast path** — when a tenant has exactly one property it is selected silently
and the switcher is not rendered. The server layout reads the cookie, validates the slug against the
tenant's own list (a stale or forged cookie falls back to the first property; it can never widen
access, because RLS scopes the list), and passes the resolved property down.

*Rejected for now:* URL-scoped routes (`/console/[propertySlug]/bookings`). More correct, and it
makes links shareable to the right property — but it doubles route depth for a case that is rare at
3–5 design partners. **Written trigger to switch: the first tenant with more than two properties, or
the first time someone needs to send a colleague a link that lands on the right one.**

Note the mixed addressing in the API: inventory, availability, front-desk and rate-plans take a
property **slug**; the knowledge base takes a property **id**. The resolved property object carries
both, so no screen has to guess.

---

## 4. Screens

Ordered by whom they serve, front desk first — roadmap R3's metric is *"daily active use by
front-desk staff — not owners. Owners buy; staff decide whether it survives."*

### 4.1 `/console/login`

**Three fields: workspace, email, password.** The workspace slug is not optional and not a nicety —
V1 keys users on `unique (tenant_id, lower(email))`, so email alone is not a login identity
(phase-4-foundations §3.2). One line of helper text says so, or every first-time user asks.

`LoginRequest` is `{ tenantSlug, email, password }`; all three are `@NotBlank`.

**One message for every failure.** The backend deliberately returns a single constant refusal —
`AuthLoginIT.inactiveAccountIsNotAnEnumerationOracle`. The UI must not helpfully distinguish
"unknown workspace" from "wrong password" and reintroduce the oracle at the top of the stack. It
renders whatever `detail` the ProblemDetail carries, and adds nothing of its own.

**States:** idle · submitting (button disabled, no double-submit) · refused · network error.
`?next=` is honoured after success, but only when it starts with `/console/` — an open redirect is
one unvalidated query parameter away.

### 4.2 `/console` — today

The front desk's daily driver, and the screen that decides whether this survives contact with a
shift.

`GET /api/v1/properties/{slug}/front-desk?date=` returns:

```
FrontDeskResponse { propertyId, propertySlug, date,
                    arrivals: BookingResponse[], departures: BookingResponse[], inHouse: BookingResponse[] }
```

`date` is optional and **defaults to today in the property's timezone** — the controller says so
explicitly, and it is the only definition of "today" a front desk recognises. The console still
sends the date it computed (§7.2) so that what the screen shows and what the header says can never
disagree.

| Panel | Source | Action |
| --- | --- | --- |
| Arrivals | `arrivals[]` — guest, reference, room type, beds from `allocations[]`, balance | Check in |
| Departures | `departures[]` | Check out |
| In house | `inHouse[]` | → booking detail |
| Tonight | **a second call** — `availability?from=<today>&to=<tomorrow>` | → calendar |
| Unpaid | `arrivals[]` filtered on `paymentState !== 'PAID'` | → booking detail |

**Occupancy is not a field on `FrontDeskResponse`** (§1.3). It comes from the one-night availability
call — summing `availableUnits` and `totalUnits` across room types — and **not** from counting
bookings, which gets the answer wrong the moment one booking holds several beds.

Check-in and check-out post to `/api/v1/bookings/{reference}/transitions` with
`{ to: 'CHECKED_IN', reason: null }`. The response carries the updated `BookingResponse` including
`earlyCheckIn`, which the UI surfaces as a note rather than a blocker — refusing an early check-in
outright is how staff end up keeping a parallel notebook (roadmap R3's kill criterion).

**Empty state is onboarding, not emptiness.** No arrivals today and no bookings at all are different
situations: the first says "nothing arriving today", the second links to setup.

### 4.3 `/console/calendar`

`GET /api/v1/properties/{slug}/availability?from=&to=[&roomTypeId=]` returns:

```
PropertyAvailabilityResponse { from, to, currency,
  roomTypes: [ { roomTypeId, code, saleMode, bookableWholeSpaces,
                 days: [ { date, availableUnits, totalUnits, availableSpaces, totalSpaces, rateMinor } ] } ] }
```

**The one distinction that will cause a bug if it is missed:** `days[].availableSpaces` is a
**per-day** count, and `bookableWholeSpaces` is a **range-wide** one — spaces free across the
*entire* requested range, which is what a `WHOLE` sale actually needs. The DTO's own javadoc says
so. **The calendar renders the per-day number; the booking wizard's `WHOLE` path must use
`bookableWholeSpaces`.** Using the per-day count to decide whether a private room can be sold for a
four-night stay will happily offer a room that is free on three of those nights.

**Rendering:** room types down, dates across, 14 days by default and up to 60. One `<table>` inside
an `overflow-x: auto` container, `position: sticky` on the first column and the header row.

- `PER_UNIT` room types show `availableUnits / totalUnits`.
- `WHOLE` room types show `availableSpaces / totalSpaces` per day, and the range-wide
  `bookableWholeSpaces` in the row header.
- Every cell shows `rateMinor`, formatted with `currency` from the response envelope (§7.1).

**This screen is where the differentiator becomes visible.** A whole-space sale allocates every unit
in the space (phase-5 §4.1), so selling one dorm bed on Tuesday makes the private-room product
disappear on Tuesday — in the same render, from the same call. That coupling deserves a legend, not
just a number.

Cell click opens `/console/bookings/new` pre-filled with that room type and date.

**No virtualization.** 60 days × ~12 room types is ~720 cells; a virtualization library at this size
is cost with no benefit. Written trigger: a property with enough room types that a 60-day render is
measurably slow — measure before reaching for it.

### 4.4 `/console/bookings`

Six server-side filters, composing: `propertyId`, `status`, `from`, `to`, `guestId`, `reference`.
**The date filter is an overlap test, not equality** — "who is here this week" means every stay that
touches the week, which is the right semantics and worth reflecting in the filter's label.

**Guest-name search is two steps**, because there is no name filter on the booking list (§1.3):
query `/api/v1/guests`, let the user pick, then filter bookings by `guestId`. That is honest and it
is fast; filtering a page client-side and calling it search is neither.

Columns: reference · guest · dates · nights · room type · status · balance. Status renders as a
labelled chip (§8.2). Row click → detail.

### 4.5 `/console/bookings/[reference]`

`BookingResponse` is rich enough to render the whole screen from one call.

| Section | Source |
| --- | --- |
| Guest | `guest: GuestDto` — `fullName`, `email`, `phone`, `countryCode`, `dateOfBirth`, `notes` |
| Stay | `checkIn`, `checkOut`, nights, `adults`, `children`, `source` |
| Lines | `lines[]` — `roomTypeCode`, `spaceId`, dates, `unitCount`, `amountMinor` |
| Beds | `allocations[]` — `unitLabel`, per-allocation dates, `releasedAt` |
| Money | `subtotalMinor`, `taxMinor`, `totalMinor`, `amountPaidMinor`, `paymentState` |
| History | `statusHistory[]` — `fromStatus`, `toStatus`, `changedBy`, `reason`, `changedAt` |
| Actions | only the transitions legal from the current `status` |

**Illegal transitions are absent, not disabled with a tooltip.** The legal set is derived from
`status` in one place (§7.3) so the UI and `BookingStatus.canTransitionTo` cannot drift into
disagreement — and when they do, the server wins and returns 409 `invalid-booking-transition`.

**A released allocation stays visible**, struck through, with its release time — that is the reason
phase-5 §4.1 chose a soft release over deleting the row. But the screen must not claim to be a full
bed history: allocations come from **current** lines only, so beds released by a date modification
drop out with their superseded line (§1.3). The section is labelled "beds on this booking", not
"bed history", because the second would be a lie.

`changedBy` is a user id. Resolving it to a name needs a user-lookup endpoint that does not exist;
until one does, the history shows the timestamp and the transition, and omits the actor rather than
printing a UUID at a front-desk operator.

### 4.6 `/console/bookings/new`

The most stateful screen in the phase. A four-step wizard over one reducer:

```
DATES ──► ROOM ──► GUEST ──► REVIEW ──► created
  ▲         ▲                   │
  └─────────┴───────────────────┘  409 no-availability / booking-conflict returns to ROOM
```

| Step | Collects | Validates before advancing |
| --- | --- | --- |
| DATES | `checkIn`, `checkOut`, `adults`, `children` | `checkOut > checkIn`; not before **property** today; nights ≤ a sane cap |
| ROOM | `roomTypeId`, `spaceId` for `WHOLE`, `unitCount` | availability > 0 — per-day for `PER_UNIT`, `bookableWholeSpaces` for `WHOLE` (§4.3) |
| GUEST | `GuestDto`, or picks an existing guest | `fullName` required; email or phone present |
| REVIEW | nothing | quote fetched and displayed |

**State lives in one reducer, and the dates live in the URL** (`?from=&to=`), so a refresh or a
shared link lands in the same place. The rest is component state — a half-finished booking is not
worth persisting.

**The quote is never computed in the browser.**

```
POST /api/v1/bookings/quote
  { propertyId | propertySlug, roomTypeId, ratePlanId?, checkIn, checkOut, unitCount }
→ { subtotalMinor, taxMinor, totalMinor, currencyCode, nightlyRates: [ { date, rateMinor } ] }
```

It allocates nothing, and it returns the same arithmetic that will be charged (phase-5 §6).
`nightlyRates[]` is what makes the review step honest: the guest sees the per-night breakdown, not
just a total, and "why is it this much" is answerable on the screen. A frontend that adds up nightly
rates itself will disagree with the backend on a rounding boundary — and the guest will be looking
at the disagreement. `ratePlanId` is optional; omitted, the backend uses the room type's default
plan.

**Confirm sends `CreateBookingRequest` with an `idempotencyKey`** generated once when the wizard
reaches REVIEW and reused on every retry of that same booking. A double-click then returns the
original booking rather than creating a second one — `BookingService.createBooking` short-circuits
on the key before doing anything else.

**409 is an ordinary outcome, not an error state.** Between quote and confirm the bed can go. Both
`no-availability` and `booking-conflict` land here; the wizard returns to ROOM, re-fetches
availability, and says plainly what happened.

### 4.7 `/console/settings/property` — OWNER

`PropertyResponse` has twenty-two fields; the form groups them so it does not read as a database
table:

- **Identity** — `name`, `legalName`, `slug` (read-only after creation), `description`, `status`.
- **Location** — `addressLine1`, `addressLine2`, `city`, `stateRegion`, `postalCode`, `countryCode`.
- **Contact** — `contactEmail`, `contactPhone`.
- **Operations** — `timezone`, `currencyCode`, `checkInTime`, `checkOutTime`, `taxRateBps`.
- **Amenities** — `amenities: string[]` of codes, multi-select from `GET /api/v1/amenities`, grouped
  by the `category` field that endpoint already returns.

**Timezone and currency get real pickers, not free text.** Timezone from
`Intl.supportedValuesOf('timeZone')`, currency from a short curated list. They are the two fields
phase-5 §2 gave no default *because* a wrong one looks right, and a text input is how a wrong one
gets typed.

`taxRateBps` is entered as a percentage and stored as basis points — the input shows `12`, the
request carries `1200`. The backend validates `@Min(0) @Max(10000)`. Conversion lives in one tested
helper, because a factor-of-100 error in a tax field is a plausible and expensive bug.

`PUT` takes the full `UpdatePropertyRequest`, so the form submits every field including the ones the
user did not touch. Load-modify-save, not a patch.

### 4.8 `/console/settings/inventory` — MANAGER+

Three editors on one screen, in the order an owner sets a property up.

**Room types.** `RoomTypeDto` — `code`, `name`, `saleMode`, `kind`, `maxOccupancy`, `baseRateMinor`,
`description`, `isActive`, `spaceIds[]`. `saleMode` and `kind` each need one line of explanation:
sale mode is how capacity is consumed, kind is what the guest thinks they are buying. A whole-dorm
buyout (`WHOLE` + `DORM`) is the example that makes the distinction land.

**Spaces and units.** `SpaceDto` returns `capacity` and nested `units[]`, and **capacity is displayed
as the derived count and is not editable** — it is not stored (phase-5 §3.2). Each `UnitDto` carries
`label`, `unitKind` and `isActive`. A space with zero units gets a warning, because it can never be
sold.

**The hybrid mapping — the one screen with no equivalent in the software these owners have used.**
`RoomTypeDto.spaceIds[]` is the same relation read from the other side, so the screen can render it
per space:

```
Space 101   6 beds    Sold as:  [ 6-bed mixed dorm ×]  [ Private 6 ×]  [+ add]
Space 201   2 beds    Sold as:  [ Private double ×]    [+ add]
Space 305   4 beds    Sold as:  — nothing. This room cannot be sold.   [+ add]
```

Backed by `POST`/`DELETE /api/v1/room-types/{id}/spaces/{spaceId}`. It needs a short explainer with
the worked example — *"Room 101 is six dorm beds on Tuesday and one private room on Saturday. Add it
to both, and selling either one hides the other for those dates."* — and the zero-mapping warning is
not optional, because a space mapped to nothing is invisible inventory.

`InventoryIntegrityIT` already asserts the invariants underneath this screen (every active space has
a unit, mappings stay within one property), so the UI can warn without having to enforce.

### 4.9 `/console/settings/rates` — MANAGER+

Two calls in, one out.

- `GET /api/v1/properties/{slug}/rate-plans` → `RatePlanDto[]` — `roomTypeId`, `code`, `name`,
  `isDefault`, `isActive`. One plan per room type is marked default; the database enforces at most
  one (`rate_plan_one_default_per_room_type`).
- `GET /api/v1/rate-plans/{id}/calendar?from=&to=` → `RateCalendarDto[]` of `{ stayDate, amountMinor }`
  — **only the dates that have an override.** Dates absent from the response fall back to the room
  type's `baseRateMinor`, and the month grid renders that fallback in muted text so "where does this
  price come from" is answerable by looking.
- `PUT /api/v1/rate-plans/{id}/calendar` with `{ from, to, amountMinor }` — **one request for a
  range**, expanded server-side. Never one request per date.

Amounts are entered in major units and sent as minor (§7.1). Creating a rate plan
(`POST …/rate-plans` with `{ roomTypeId, code, name, isDefault }`) is on the same screen, because a
room type with no plan has no price to override.

### 4.10 `/console/knowledge-base` — MANAGER+

Track C's persisted editor with its version history:
`GET`, `POST`, and `GET /history` on `/api/v1/properties/{propertyId}/knowledge-base`. **This path
takes a property id, not a slug** — the one endpoint in the console that does (§3.1).

`KnowledgeBaseVersionResponse` gives `versionNo`, `content`, `contentSha256`, `charCount`,
`authoredBy`, `createdAt`.

- Save is **explicit**, not on blur — `contentSha256` makes an unchanged save a no-op, and the UI
  says so ("no changes to save") rather than pretending to have saved.
- History lists version number, author and timestamp; a version can be read and its content copied
  into the editor. **Restoring is a new save**, not a rewind — the history stays append-only.
- A 409 `knowledge-base-conflict` means someone else saved first, and it survives the service's own
  one retry. Show it, and offer to reload.
- The character counter mirrors the 20,000 limit that exists in three places already: `@Size`, the
  `char_count` check constraint, and the anonymous admin panel.

**The anonymous editor at `/` is a different component and stays exactly as it is.** Two editors is
more code; an auth hiccup during an unrepeatable beta hour is worse.

---

## 5. Contracts

`src/lib/contracts.ts` holds the chat contracts and is not touched. New modules under
`src/lib/contracts/`, one per domain, mirroring the Java records **by hand**:

| Module | Mirrors |
| --- | --- |
| `auth.ts` | `LoginRequest`, `AuthUserResponse` |
| `property.ts` | `PropertyResponse`, `UpdatePropertyRequest`, `CreatePropertyRequest`, `AmenityResponse` |
| `inventory.ts` | `RoomTypeDto`, `SpaceDto`, `UnitDto` |
| `availability.ts` | `PropertyAvailabilityResponse`, `RoomTypeAvailabilityDto`, `DayAvailabilityDto` |
| `booking.ts` | `BookingResponse`, `CreateBookingRequest`, `CreateBookingLineRequest`, `ModifyBookingRequest`, `TransitionRequest`, `GuestDto`, `BookingLineResponse`, `AllocationResponse`, `BookingStatusHistoryResponse`, `FrontDeskResponse` |
| `rate.ts` | `RatePlanDto`, `CreateRatePlanRequest`, `RateCalendarDto`, `SetRateCalendarRequest`, `QuoteRequest`, `QuoteResponse`, `NightlyRate` |
| `knowledgeBase.ts` | `KnowledgeBaseVersionResponse`, `SaveKnowledgeBaseRequest` |
| `problem.ts` | RFC 9457 `ProblemDetail`, including the `errors` map the validation handler adds |

No code generation. The mirroring is manual and therefore fallible, so **each module has a test that
parses a recorded sample response** — fixtures in `src/lib/contracts/__fixtures__/`, captured from
real responses rather than hand-written, so a backend field rename fails a test instead of a screen.

Types that need care because JSON flattens them:

- `UUID` → `z.string().uuid()`.
- `LocalDate` → `z.string().regex(/^\d{4}-\d{2}-\d{2}$/)`, kept as a **string end to end**. It is a
  calendar date, not an instant, and turning it into a `Date` re-introduces the timezone bug §7.2
  exists to prevent.
- `OffsetDateTime` → `z.string().datetime({ offset: true })`.
- `LocalTime` → `z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/)` — `checkInTime`, `checkOutTime`.
- `long` minor amounts → `z.number().int()`. Safe: `Number.MAX_SAFE_INTEGER` is ~9×10¹⁵ minor units.
- `char(3)` / `char(2)` → `z.string().length(3)` / `.length(2)` for `currencyCode`, `countryCode`.
- `Set<String> roles` → `z.array(z.string())`; Jackson serializes a `Set` as a JSON array.
- Nullable Java fields → `.nullable()`, not `.optional()`. Jackson emits `null`, it does not omit
  the key — `releasedAt`, `spaceId`, `authoredBy`, `changedBy`, `reason` and most of the property
  address fields are all of this shape.

**Validation runs in both directions** — the request from the browser and the response from Spring.
That is a Phase 2 convention and it earns its keep here: it turns a backend contract change into a
loud test failure instead of a blank screen.

---

## 6. BFF route handlers

Mutations need explicit handlers so both directions can be validated. Reads mostly go straight from
a Server Component through `upstream()`, which needs no handler at all.

A shared helper keeps each handler at about ten lines:

```
proxy({ method, path, requestSchema, responseSchema, status })
```

It parses the incoming body against `requestSchema`, calls `upstream()`, parses the response against
`responseSchema`, and maps a non-2xx into the ProblemDetail the client expects.

| Next route | Upstream |
| --- | --- |
| `POST /api/console/login` | `POST /api/v1/auth/login` — plus the `Set-Cookie` relay (§2.1) |
| `POST /api/console/logout` | `POST /api/v1/auth/logout` — plus cookie expiry |
| `PUT /api/console/properties/[slug]` | `PUT /api/v1/properties/{slug}` |
| `POST/PUT /api/console/properties/[slug]/room-types/…` | room-type create/update |
| `POST/DELETE /api/console/room-types/[id]/spaces/[spaceId]` | the hybrid mapping |
| `POST/PUT /api/console/properties/[slug]/spaces/…` | space create/update |
| `POST /api/console/properties/[slug]/rate-plans` | rate-plan create |
| `PUT /api/console/rate-plans/[id]/calendar` | `SetRateCalendarRequest` |
| `POST /api/console/quote` | `POST /api/v1/bookings/quote` |
| `POST /api/console/bookings` | `POST /api/v1/bookings` |
| `PATCH /api/console/bookings/[reference]` | modify |
| `POST /api/console/bookings/[reference]/transitions` | transition |
| `POST/PUT /api/console/guests/…` | guest create/update |
| `POST /api/console/knowledge-base/[propertyId]` | KB save |

*Rejected:* a single catch-all `/api/console/[...path]` passthrough. It is a third of the code and it
throws away the bidirectional validation that is the whole point of having a BFF here. The `proxy()`
helper gets most of the brevity without the loss.

---

## 7. Shared helpers, and the three that prevent real bugs

### 7.1 Money — `src/lib/staff/money.ts`

Every amount crossing the wire is `long` **minor units** plus an ISO 4217 code. Nothing in the UI
divides by 100 inline.

```
formatMinor(1250000, 'INR')  → '₹12,500.00'      Intl.NumberFormat, currency style
parseMajor('12500', 'INR')   → 1250000           for the rate and payment inputs
```

The exponent comes from `Intl.NumberFormat().resolvedOptions().maximumFractionDigits` rather than a
hardcoded 100 — most currencies are 2, some are 0, and a hostel chain in Vietnam is not implausible.
Both directions are unit-tested including a zero-decimal currency.

The currency code comes from the response envelope in each context — `PropertyAvailabilityResponse.currency`,
`QuoteResponse.currencyCode`, `BookingResponse.currencyCode`, `PropertyResponse.currencyCode`. It is
never assumed from the property in a screen that was handed one.

### 7.2 Dates — `src/lib/staff/dates.ts`

**The browser's timezone is never used for a business date.** Ever.

```
propertyToday(timezone)          → '2026-08-30'   via Intl.DateTimeFormat('en-CA', { timeZone })
nightsBetween(checkIn, checkOut) → integer        half-open [in, out), matching daterange '[)'
formatStayRange(in, out, tz)     → display string
```

`new Date().toISOString().slice(0, 10)` is UTC and is the exact bug `property.timezone` was added to
prevent. It appears once in the codebase today — in `api.ts`'s session-id fallback, where it is a
bucket key and harmless — and it must not be copied into anything that decides a business date. A
lint rule banning `toISOString().slice` outside `lib/api.ts` is cheap and worth it.

Dates stay strings from the API to the screen and back. They are parsed into a `Date` only for
rendering, and never for arithmetic — `nightsBetween` counts days on the string form.

### 7.3 Booking status — `src/lib/staff/bookingStatus.ts`

One place holding the display label, the chip colour, and the legal transitions from each status,
mirroring `BookingStatus.canTransitionTo`:

```
BOOKED      → CHECKED_IN | CANCELLED | NO_SHOW
CHECKED_IN  → CHECKED_OUT
CHECKED_OUT | CANCELLED | NO_SHOW → terminal
```

Two copies of a state machine drift. This one is mirrored deliberately, in one file, with a comment
naming its Java counterpart — and the server is authoritative, so a drift produces a 409 rather than
a wrong write.

---

## 8. Visual conventions

`globals.css` today defines `--background` and `--foreground` and nothing else. The console needs a
slightly larger set. **Additive only** (§0).

### 8.1 Tokens

`--surface`, `--surface-muted`, `--border`, `--text-muted`, `--accent`, `--danger`, `--warning`,
`--success`, each with its `prefers-color-scheme: dark` counterpart alongside the existing pair. Ten
tokens, not a design system.

### 8.2 Status chips

| Status | Treatment |
| --- | --- |
| `BOOKED` | accent outline |
| `CHECKED_IN` | success, filled |
| `CHECKED_OUT` | muted |
| `CANCELLED` | muted, struck through |
| `NO_SHOW` | warning |

**Never colour alone.** Every chip carries its label, every released allocation carries its
strike-through *and* its release time. A front desk under fluorescent light on a five-year-old
monitor is the real rendering environment.

### 8.3 Layout

A persistent left nav (Today · Calendar · Bookings · Guests · Settings), a header with the property
switcher and the signed-in user, and the page. Nav items the role cannot use are not rendered —
which is presentation, not enforcement (§9).

Target 1280×800 and tablet width. A front desk is not on a phone, and roadmap §8 is explicit that
the responsive web console *is* the staff app — so tablet has to work, and a phone only has to not
be broken.

---

## 9. The UI is not the security boundary

The console hides what a role cannot do. **That is presentation.** Enforcement is `@PreAuthorize` on
the server, and phase-5 §8's role matrix has a 403 test behind every refusal.

Note what the matrix actually says, because the console must not be more restrictive than the API
without a reason: **`FRONT_DESK` can read availability, rate plans and the rate calendar** — it
needs prices to quote a walk-in — and is refused only on the writes (`POST`/`PUT` on room types,
spaces, rate plans and the calendar) and on property configuration. The Settings *section* is
therefore hidden from `FRONT_DESK`, but the rate figures it needs appear on the booking wizard,
which it can use.

Route-level guards redirect an unauthenticated visitor to `/console/login` and an under-privileged
one to `/console` with a plain message. Neither is trusted for anything. §11 includes a test that
issues a `FRONT_DESK` request **directly to the BFF route** for an owner-only mutation and asserts
403 — because a hidden menu item is not an answer to "can a front desk change rates?"

---

## 10. Error, empty and loading states

The backend's registered problem types, read off `GlobalExceptionHandler`. Each one the console can
meet gets a deliberate rendering; nothing falls through to a raw status code.

| Type | Status | Console behaviour |
| --- | --- | --- |
| `validation-error` | 400 | Field-level errors from the `errors` map, onto the form |
| `unauthorized` · `missing-tenant` | 401 | Clear the cookie, redirect to login with `next=` (§2.4) |
| `forbidden` | 403 | "You don't have access to that" — never a blank screen |
| `not-found` | 404 | Empty state for that resource, with a way back |
| `no-availability` · `booking-conflict` | 409 | **Normal outcome** on the booking path (§4.6) |
| `invalid-booking-transition` | 409 | The status moved under us; re-fetch and re-render actions |
| `knowledge-base-conflict` | 409 | Someone else saved first; offer to reload (§4.10) |
| `model-rate-limited` | 503 | "The concierge is paused right now." |
| `model-unavailable` | 502 | "The concierge is offline for a moment." |
| our rate limiter | 429 | "One moment — catching up." |
| read timeout | 504 | "The request timed out. Please retry." |

The last four are already distinguished in `api.ts` (Track D). **The console must not collapse them
back into one string** — that collapse is exactly the bug phase-4-completion §2.1 was written to fix.

- **Empty states are onboarding.** A property with no room types shows the way to create one, not an
  empty table. This is the first screen a new tenant sees after provisioning, and roadmap §7 wants
  onboarding to become guided setup — this is where that starts.
- **Loading is a skeleton of the eventual layout**, not a spinner over a blank page; the screens are
  dense and a spinner loses the reader's place.
- **Nothing personal in a URL.** Guest names, emails and phone numbers stay out of query strings —
  they land in server logs and browser history. Bookings are addressed by `reference`, guests by id.
  This is the frontend half of the discipline `GuestPrivacyLoggingTest` enforces on the backend.

---

## 11. Testing

`vitest` + Testing Library with the existing `jsdom` environment and `@` alias — 23 tests pass
today, and this phase adds to that file set without touching the three that exist. Nothing here
needs a running backend; upstream calls are stubbed at `fetch`.

| Test | Proves |
| --- | --- |
| `/concierge` renders with no session | §9.1 constraint 1 — the standing guard, relocated |
| `/` redirects to `/concierge` | §0.1 — the runbook's "open localhost:3000" stays true |
| Login handler relays the upstream cookie under `altstay_session` | The session works at all |
| The cookie is `httpOnly`, `SameSite=Strict`, `Path=/`, and `Secure` in production | §2.2, asserted on headers not assumed |
| Page JavaScript cannot read the upstream session id | §2.5 |
| Logout clears the BFF cookie **and** calls Spring's logout | No half-dead session |
| A 401 or `missing-tenant` clears the cookie and redirects with `next=` | §2.4 |
| `?next=` outside `/console/` is refused | No open redirect |
| Built client bundle contains no backend origin; `NEXT_PUBLIC_BACKEND` appears nowhere | §2.5 |
| Each contract module parses its recorded fixture | The hand-mirrored contracts are right |
| Nullable Java fields parse when `null`, not only when absent | §5 — the `.nullable()` vs `.optional()` trap |
| A `FRONT_DESK` session gets 403 from an owner-only BFF route | §9 — enforcement, not menu-hiding |
| `formatMinor` / `parseMajor` round-trip, incl. a zero-decimal currency | §7.1 |
| `propertyToday` returns the property's date, not the browser's, across a UTC-day boundary | §7.2 — the bug the timezone field exists for |
| `nightsBetween` on a one-night stay and across a month boundary | Half-open range, the classic off-by-one |
| Legal transitions match `BookingStatus.canTransitionTo` for all five statuses | §7.3 drift |
| **The `WHOLE` path uses `bookableWholeSpaces`, not per-day `availableSpaces`** | §4.3 — a room free on 3 of 4 nights is not offerable |
| Booking wizard refuses `checkOut <= checkIn` before submitting | The commonest input error |
| Booking wizard reuses one `idempotencyKey` across a retry | §4.6 |
| A 409 at REVIEW returns to ROOM and re-fetches, without throwing | The race is an outcome |
| Occupancy comes from availability, not from counting bookings | §4.2 — multi-bed bookings |
| Calendar renders a `WHOLE` type as unavailable when a bed in its space is sold | The differentiator, in the UI |
| A space with no room-type mapping renders its warning | §4.8 — invisible inventory |
| Rate grid shows `baseRateMinor` where the calendar has no override | §4.9 — the fallback is visible |
| Role-gated nav hides what the role cannot do | Presentation only; the 403 row above is the real one |

---

## 12. Build order

Eight slices. Each ends with something that runs, and **none is blocked** — every endpoint exists
(§1.2).

| # | Slice | Delivers |
| --- | --- | --- |
| 0 | **The demo move** (§0.1): `/concierge` renders `ConsoleShell`, `/` redirects, both guard tests, runbook §4 updated | The demo is where it belongs, provably intact |
| 1 | Session relay, `session.ts`, `proxy()`, shell, nav, login, property switcher | You can log in and see your property |
| 2 | Bookings list, booking detail, transitions, guests | The whole booking surface, read and act |
| 3 | Today | The front desk's daily driver |
| 4 | Calendar | Availability, and the hybrid coupling made visible |
| 5 | New booking wizard | The console can take a booking end to end |
| 6 | Settings: property, inventory, the hybrid mapping | A tenant can be set up without SQL |
| 7 | Settings: rates, knowledge base | Prices and the concierge's rulebook |

**Slice 0 first, and on its own.** It is an hour of work and it touches the one thing in this
repository that is genuinely unrepeatable if broken. Doing it alone means the runbook §4 re-walk that
proves the demo survived is walked against a diff containing nothing else — if the demo misbehaves
afterwards, the cause is unambiguous. Folding it into slice 1 buries a demo-path change inside a
session-plumbing change, which is how a subtle break gets attributed to the wrong thing.

Slice 1 next: until the cookie relay is right, every other screen is untestable against a real
backend. Slices 2–5 are the front-desk path and are what make this demoable; slices 6–7 are the
owner's path and complete the white-glove onboarding roadmap §7 asks for.

The order differs from the previous draft, which sequenced settings before the front desk because
the front-desk endpoints did not exist yet. They do now, so the screens that decide whether this
survives a shift come first.

### 12.1 Slice status and gaps carried forward

**Slice 0 — delivered 2026-08-30.** `/concierge` renders `ConsoleShell` byte-identical to the old
`/`; `/` redirects. Both guard tests written red-first. `git diff --stat` touched only
`src/app/page.tsx` and the new `src/app/concierge/page.tsx`, plus a jsdom `scrollTo` polyfill in
`vitest.setup.ts` (a test-environment gap `MessageList`'s autoscroll effect hit the moment it was
rendered under RTL for the first time — not a demo behaviour change). Runbook §4 re-walk is still
the user's to do, in a browser, with a Gemini key that has quota.

**Slice 1 — delivered 2026-08-30.** Session relay (`session.ts`, `proxy()`), the optimistic
`/console/**` gate (`src/proxy.ts`), login/logout/property-switch BFF routes, the `(app)` shell
(header, role-gated nav, property switcher), and `clientFetch()` (any client-side 401 redirects to
login — §2.4). 91 tests, all watched red first. Three gaps, deliberately deferred rather than
overlooked:

1. **Contract fixtures in `src/lib/contracts/__fixtures__/` are hand-written against the Java DTO
   and handler source, not captured from a live call**, contrary to §5's "captured from real
   responses rather than hand-written." Provisioning a tenant to capture one would have written
   real, hard-to-fully-reverse data into the shared Neon instance without asking first — bigger
   than this slice warranted. **Trigger to recapture:** the first time a tenant already exists for
   another reason (e.g. the §13 walkthrough's provisioned tenant) — pull a real `/me`,
   `/properties`, and a validation-error response from it and replace these three fixtures.
2. **The "`FRONT_DESK` gets 403 from an owner-only BFF route" test (§11, §13) does not exist yet.**
   There is no owner-only *mutation* BFF route until settings (slice 6) exists to need one — login,
   logout, and the property switch are all reachable by any authenticated role. Lands with slice 6.
3. **`next=` fidelity covers the common case (never logged in) exactly, via `src/proxy.ts` reading
   the real request path — the case §2.3 was actually worried about does not have this gap.** The
   one path that still falls back to a bare `/console/login` is a session that dies *between* one
   page load and the next server-rendered navigation on the same visit: `requireSession()` in the
   `(app)` layout has no pathname to work with (Server Component layouts don't receive one), so
   that redirect can't carry `next=`. Rare — Proxy's cookie-presence check has already screened out
   the common case by the time a Server Component runs at all — and a minor UX gap, not a
   functional one: the user lands on login, not on an error.

**Slice 2 — delivered 2026-08-30.** Bookings list (`/console/bookings`, six-filter GET form plus
the two-step guest-name picker), booking detail (`/console/bookings/[reference]`, all seven
sections, transition actions), and a guests directory (`/console/guests`,
`/console/guests/[id]`) with create and edit — no design spec existed for the guests screens
beyond the route map and the role matrix, so they're deliberately minimal: a list with an inline
add-guest form, and load-modify-save editing on the detail page, matching the convention used
elsewhere rather than inventing a new one. `money.ts`, `dates.ts` and `bookingStatus.ts` (§7) all
landed here, plus the §8.1 tokens in `globals.css` (additive) since this is the first screen dense
enough to need them. 50 new tests, all watched red first. Two things worth recording:

1. **There is no way to record a payment, anywhere in the delivered API — verified by reading
   `BookingService.java` and `Booking.java`, not assumed.** `amountPaidMinor` and `paymentState`
   are set once at booking creation (`0` / `"UNPAID"`) and never written again by any code path;
   neither field appears in `CreateBookingRequest` or `ModifyBookingRequest`. This contradicts
   phase-5 §8's role matrix, which lists "Record a payment amount" as a capability all three roles
   have, and this plan's own §14, which says "`amountPaidMinor` is a number a human types" as if an
   input exists somewhere for it to be typed into. It doesn't. The booking detail screen renders
   both fields verbatim — always `UNPAID`, always the paid amount as it was at creation — and does
   **not** grow a payment-entry control, because there is nothing on the backend for it to call.
   This is a backend gap, not a frontend one; per the working agreement, flagging it here rather
   than adding the endpoint myself.
2. **§7.3's pseudocode transition table was stale against the delivered code, and the mirror in
   `bookingStatus.ts` follows `BookingStatus.java`, not the plan text.** The enum's javadoc names
   the deviation explicitly: `CHECKED_IN -> CANCELLED` is legal (a stay voided after check-in — a
   payment that never cleared, someone asked to leave), on top of the `CHECKED_IN -> CHECKED_OUT`
   the plan's diagram already showed. `legalTransitionsFrom('CHECKED_IN')` returns both, matching
   `canTransitionTo`. The plan's own instruction to verify against code rather than a stale
   document is what caught this before it shipped as a UI that hid a legal front-desk action.

`ModifyBookingRequest` (PATCH) was not needed for the walkthrough's "check out a night early"
step: `transitionBooking`'s `CHECKED_OUT` branch already shortens the current lines and
allocations to end today when checking out before the booked departure, in the same transaction.
Modifying a `BOOKED` reservation's own dates before arrival is a real capability the API exposes,
but no screen in this plan calls for it, so it is not built — same reasoning as `CreateBookingRequest`
waiting for the wizard (slice 5).

**Slice 3 — delivered 2026-08-30.** The Today screen (`/console`, replacing slice 1's
placeholder): Arrivals, Departures, In house, Tonight (a second, one-night availability call,
summed across room types — never a count of bookings), and Unpaid, plus the onboarding-vs-empty
distinction (§4.2, §10) verified by checking `/api/v1/bookings?propertyId=` only when the day view
is otherwise empty. Check-in/check-out are one-click (`QuickCheckInOutButton`, absent rather than
disabled when illegal, reusing `legalTransitionsFrom`); an early check-in is noted for 1.5s before
the list refreshes, since the front-desk list itself has no way to show it after the fact (below).
14 new tests, all watched red first.

1. **`FrontDeskResponse`'s bookings carry an empty `allocations[]`, always** —
   `BookingService.summaryOf` passes `List.of()` for both `allocations` and `statusHistory` and a
   hardcoded `false` for `earlyCheckIn`, verified by reading the method rather than assumed from
   §4.2's column description ("beds from `allocations[]`"). Room type and unit count come from
   `lines[]` instead (`FrontDeskRow` renders `"MIXED-6 ×1"` style summaries); no bed label is
   available on this screen at all, only on the booking detail page, which fetches the full
   `BookingResponse` rather than a front-desk summary. `earlyCheckIn` is real only on a
   transition's own response, which is why the note is shown at the moment of that response and
   not derived from any later read of the list.
2. The "Unpaid" panel filters on `paymentState !== 'PAID'`, exactly as specified — and, per the
   payment-recording gap above, currently matches every arrival, every time. Left as specified
   rather than special-cased, since it becomes correct the moment that gap closes and there is
   nothing to "fix" in the meantime.

**Slice 4 — delivered 2026-08-30.** The calendar (`/console/calendar`): room types down, dates
across, sticky header row and first column, a `from`/`days`(14·30·60, clamped)/`roomTypeId` filter
form, `PER_UNIT` rows showing units and `WHOLE` rows showing spaces plus the range-wide
`bookableWholeSpaces` in the row header, every cell linking to the (not-yet-built) booking wizard
pre-filled with room type and date, and a written legend for the whole-space/dorm-bed coupling
rather than a hover trick. `roomTypeId` filtering happens over the one already-fetched response,
not as a second backend call — the same unfiltered list also supplies the filter dropdown's
options. 9 new tests, all watched red first.

**One deliberate deviation from §3's route map, recorded rather than silently done:** the grid
(`CalendarTable`) is a Server Component, not the "Client grid" the architecture note calls for.
Every cell is a plain link and the date range lives entirely in the URL, so nothing on this screen
needs client-side state — marking it `'use client'` would have bought nothing but a bundle-size
cost, which cuts against this plan's own instinct against unneeded client machinery (§3's "no
client-side data-fetching library... at this size"). If a later requirement needs real interaction
(drag-select, hover previews), that is the trigger to actually make it a Client Component.

**Slice 5 — delivered 2026-08-30.** The new-booking wizard (`/console/bookings/new`): one reducer
(`bookingWizardReducer`, unit-tested independently of any component) driving DATES → ROOM → GUEST →
REVIEW → CREATED, dates in the URL, a quote fetched fresh every time REVIEW is (re-)entered and
never summed client-side, an `idempotencyKey` minted once per fresh pass into REVIEW and reused by
any retry that doesn't go through GUEST again, and a 409 at confirm returning to ROOM with
`router.refresh()` pulling fresh availability into the same component instance rather than losing
wizard state to a remount. 29 new tests, all watched red first — including one that caught a real
bug before it shipped: the first draft of the 409 path set `state.error` but nothing rendered it,
because `RoomStep` never received that prop. The integration test looking for the message via
`findByRole('alert')` timed out, which is what red-first is for.

Two things worth recording:

1. **`DatesStep`'s custom validation (not-before-today, sane-nights-cap) was silently unreachable
   until `noValidate` was added to its `<form>`.** The native HTML5 `min` constraint on the date
   input blocks the `submit` event entirely before React's handler ever runs, so the friendlier
   message was dead code the whole time it was missing — caught by the same red-first test that
   exercises the check-in-before-today case, which timed out waiting for an alert that native
   validation had silently swallowed. `LoginForm` already had `noValidate`; this is now the second
   form in the console to need it, which makes it a pattern worth remembering for the next one.
2. **The `WHOLE` path never offers a specific space to book — `spaceId` is always sent as `null`,
   letting the backend auto-assign any available space mapped to the room type**
   (`allocateUnitsForLine`'s existing fallback). Building a real picker needs `RoomTypeDto.
   spaceIds[]` and `SpaceDto` names, i.e. the inventory contracts settings/inventory (slice 6)
   builds anyway — pulling that forward here to save one dropdown wasn't worth duplicating. Trigger
   to add it: the first time an owner asks to assign a *specific* room rather than any free one.

**Slice 6 — delivered 2026-08-30.** Settings: property (`/console/settings/property`, OWNER —
identity/location/contact/operations/amenities, real timezone and currency pickers, the
tax-percent↔bps conversion isolated in `percentToBps`/`bpsToPercent`) and inventory
(`/console/settings/inventory`, MANAGER+ — room types, spaces and units, and the hybrid mapping).
`requirePropertyContext` grew an optional `roles` parameter so both role-gated pages share the
same session+property resolution the open ones already had. 39 new tests, all watched red first —
two of which caught real bugs before they shipped:

1. **`proxy()` threw on any bodiless *non*-204 success.** The hybrid-mapping `POST` returns 201
   with an empty body; `NextResponse.json(undefined)` throws, and the existing 204 special-case
   didn't cover it. Fixed in `proxy()` itself (any empty body now gets a bare response, not just a
   204 one) rather than in the one route that happened to surface it — the fix and its test both
   live in `proxy.test.ts`, not the mapping route's own test, because the bug was generic.
2. **`InventoryService.updateSpace` deletes and recreates every unit row when `units` is
   non-null — verified by reading the method, not assumed.** A naive "always resend the current
   units on save" edit form would have silently replaced every bed's id on an unrelated
   rename-only save, which is exactly the kind of change that could orphan an allocation pointing
   at the old id. `UpdateSpaceRequestSchema.units` is `.nullable()` for this reason, and the
   settings screen enforces the split at the UI level: a plain name/floor/status save always sends
   `units: null`; only a separately-labelled "Manage beds" action — with a visible warning — sends
   the full replacement array. A test locks in that the plain save never sends a non-null `units`.

**Slice 7 — delivered 2026-08-30, the last slice.** Settings/rates (`/console/settings/rates`,
MANAGER+ — rate-plan creation, a month grid falling back to `baseRateMinor` in muted text where no
override exists, and setting one amount across an inclusive range in a single `PUT`) and the
knowledge base (`/console/knowledge-base`, MANAGER+ — explicit save with a codepoint-accurate
20,000-character counter, append-only version history, "copy into editor" as a staging action
rather than a rewind, and 409 `knowledge-base-conflict` handling with a Reload action). `dates.ts`
gained `startOfMonth`/`endOfMonth`. `ConsoleNav` was corrected in the same slice: MANAGER previously
saw a single "Settings" link that led to an OWNER-only page it would immediately bounce off of —
it's now three separate links (Property settings: OWNER only; Inventory and Rates: MANAGER+),
plus the new Knowledge base link. 45 new tests, all watched red first.

One thing worth recording: **`RateCalendarDto`'s `stayDate` range is inclusive on both ends**
(`RateService.setCalendarRange` loops `!d.isAfter(to)`), unlike every booking date range in this
console, which is half-open `[checkIn, checkOut)`. Verified by reading the service rather than
assumed from the naming symmetry with everything else — the one place in the console where "to"
means something different from everywhere else, called out in the contract, the component, and
the page.

**All eight slices are now code-complete.** Two §13 items remain outside what a coding session can
close by itself and are the user's to do:

1. **The full walkthrough in a browser against a tenant created by the provisioning runner.**
   Running the provisioning runner writes a real tenant into the shared Neon instance — the same
   category of action slice 1 already declined to take unilaterally when it would have been
   useful for capturing contract fixtures (§12.1, slice 1). It needs a go-ahead before being run,
   not just the code being ready.
2. **The demo re-walk (carried since slice 0) and the console's own live walkthrough both need a
   real browser and a Gemini key with quota** — this session can write and watch-red-first every
   test that doesn't require one, but "does this look and feel right to a person" is not one of
   them.

Everything else in §13 — the standing guards, the cookie contract, the contract fixtures, the
403/401 behaviour, no PII in a URL, `propertyToday` at a UTC boundary, and the differentiator's own
automated test — is closed and verified by the commands recorded at each slice above, including a
final `.\mvnw.cmd clean verify` re-run in this slice: **177 unit + 53 IT (52 correctly skipped),
BUILD SUCCESS**, confirming the backend is exactly as untouched as every earlier slice claimed.

---

## 13. Definition of Done

```powershell
cd frontend; npm run test; npm run build; npm run lint
```

```powershell
cd backend; .\mvnw.cmd clean verify
```

```powershell
cd frontend; Get-ChildItem -Recurse src -File | Select-String -Pattern "NEXT_PUBLIC_BACKEND"
```

- [ ] `npm run test`, `npm run build` and `npm run lint` all pass, and the backend's 177 unit +
      50 integration tests stay green — this phase changes no backend code
- [ ] **The demo is unchanged in behaviour at its new address** — [dev-runbook.md](dev-runbook.md)
      §4 re-walked in full at `/concierge` (live knowledge-base sync, escalation, preset switching,
      mobile layout, keyboard flow), **and re-walked once more starting from `/`** to prove the
      redirect lands where the old instruction said it would
- [ ] **Only the two route files moved** — `git diff --stat` shows changes to `src/app/page.tsx`
      (now a redirect) and a new `src/app/concierge/page.tsx`, and **no change** under
      `src/components/chat`, `src/components/admin`, `src/components/console`, `src/hooks/`,
      `src/lib/presets.ts` or `src/lib/api.ts`. Additions to `globals.css` are new tokens only; no
      existing token's value changed
- [ ] A test asserts `/concierge` renders with no session, and a test asserts `/` redirects to it —
      the standing guard was **relocated, not deleted**
- [ ] [dev-runbook.md](dev-runbook.md) §4 names `/concierge` rather than the bare origin, and says
      why the redirect exists
- [ ] The `NEXT_PUBLIC_BACKEND` search returns nothing, and the built client bundle contains no
      `:8080` origin — the invariant `SecurityConfig`'s CSRF decision rests on
- [ ] The BFF cookie's four attributes are asserted on the response headers
- [ ] An expired session redirects to login rather than rendering an empty list
- [ ] Every contract module parses a fixture captured from a real response, `null` fields included
- [ ] **The full walkthrough runs in a browser** against a tenant created by the provisioning runner,
      with no hand-written SQL and no `curl.exe` at any step, and is written into
      [dev-runbook.md](dev-runbook.md) §8:

  log in → create a property → two room types, one `PER_UNIT` and one `WHOLE` → a space with six
  beds → map that space to **both** room types → set a rate for a week → open the calendar and see
  both products available → book a dorm bed → **watch the whole-room product disappear for those
  dates on the same calendar** → check the guest in → check out a night early → see the freed night
  become bookable again → cancel a second booking → see its bed return

- [ ] That walkthrough's central step — the whole-room product disappearing when one bed sells — is
      also an automated test (§11), because it is the product's differentiator and a manual
      observation is not a regression guard
- [ ] A `FRONT_DESK` session gets **403** from an owner-only BFF route, with the request issued
      directly, not merely a hidden menu item — and can still read the rates it needs to quote (§9)
- [ ] The four throttle/outage states render as four different messages
- [ ] No guest name, email or phone appears in any URL
- [ ] The console renders usably at 1280×800 and at tablet width
- [ ] `propertyToday` is proven against a UTC-day boundary — the arrivals list is correct at 06:00
      IST, which is 00:30 UTC the same day and 20:30 UTC the day before

Phase 6 is done when a front-desk shift could be run on it, and the anonymous demo has not noticed
that any of it exists.

---

## 14. Not in this phase

- **The marketing site** — decided 2026-08-30 to be **Phase 7**, scoped to a landing page at `/`, a
  product page covering the three pillars (concierge · inventory · bookings), and about/contact.
  Phase 6 only vacates `/` for it (§0.1) and builds none of it. Two things are open and belong in
  that phase's plan, not this one: who the site is written for — prospective owners, or the
  interviewers roadmap §3.1 names as this repo's second audience — and where a contact form
  actually sends. **No pricing page**: roadmap §3 puts the pricing decision at R2, and publishing
  numbers before the booking wedge is tested pre-commits a call that has not been made.
- **Drag-and-drop bed assignment** — roadmap R3. A grid you can read and click is what a pilot
  needs; a grid you can drag is a project.
- **Housekeeping status, turnover queue, reporting dashboards** — roadmap R3.
- **A full bed history on the booking detail** — the API returns allocations for current lines only
  (§1.3). Trigger: the first time someone asks which bed a guest was in last week.
- **Resolving `changedBy` to a name** — needs a user-lookup endpoint that does not exist (§4.5).
- **A mobile app** — roadmap §8: WhatsApp is the guest app, a responsive web console is the staff app.
- **User management UI** — provisioning creates the owner (phase-5 §10); adding staff accounts is a
  small screen nothing yet depends on. Trigger: the first tenant with a second employee.
- **Payments UI** — there is no gateway. `amountPaidMinor` is a number a human types.
- **Guest-facing booking pages** — the direct booking engine is roadmap R2 and is guest-facing, so it
  fails the §9.1 admission test that admits everything else here.
- **A client-side data-fetching library, a component library, a virtualization library** — each has a
  written trigger above, and none has fired.
