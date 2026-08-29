# Phase 5 — The PMS core: property, inventory, bookings

The backend half of turning this from a chatbot with a database into something a property can run
on. [phase-6-staff-console.md](phase-6-staff-console.md) is the other half; neither is useful alone.

**What exists today:** a `property` table with `name` and `slug`, and nothing else. No rooms, no
beds, no rates, no guests, no bookings. The concierge can answer *"what time is check-in?"* and
cannot answer *"do you have a bed on Friday?"* — which roadmap §2 names as the pivotal rung of the
entire ladder, and which no amount of prompt work reaches.

---

## 0. Why this is admissible before the R0 gate

[phase-3-validation.md](phase-3-validation.md) §9.1's admission rule is a single test:

> **Does this survive a KILL verdict?**

If the gate kills, the roadmap's pivot is *"owner-facing tooling (the ops side) instead of
guest-facing."* A property record, room and bed inventory, a booking lifecycle and a front-desk
console **are** that ops side. They are not merely compatible with a KILL verdict — they are the
thing a KILL verdict tells us to build. This is the strongest admission case any work has had under
that rule; stronger than Track A's, which rested on "any SaaS needs it."

| This phase's scope | Survives a KILL | Verdict |
| --- | --- | --- |
| Property record: address, timezone, currency, amenities | Yes — it is the ops product's root record | **Admitted** |
| Room types, spaces, bed-level units | Yes — roadmap §5 calls this the differentiator, independent of the channel | **Admitted** |
| Availability + the concurrency boundary | Yes | **Admitted** |
| Booking lifecycle, rates, guests | Yes — a PMS without bookings is not a PMS | **Admitted** |
| Repeatable tenant provisioning | Yes — it is how any tenant reaches any of the above | **Admitted** |
| The concierge *answering* availability from this inventory | **No** — it is a guest-facing bet | **Withheld until the gate** |
| WhatsApp webhooks, human handoff, guest-thread semantics | No | **Withheld** (unchanged) |

That last withheld row is the discipline this phase must hold. The inventory model is built so the
concierge *can* read it later; `ChatService` is not touched, `concierge-system.st` stays frozen, and
the chat endpoint stays exactly as stateless as it is today. Wiring inventory into the concierge is
one service call away by construction, and it waits for the gate to answer.

**Sunk cost is still not evidence.** §9.1 constraint 3 applies to this phase with more force than to
Phase 4, because this is a bigger investment. The existence of a PMS must not appear in the reasoning
that decides the gate.

---

## 1. Scope

| In scope | Out of scope, and why |
| --- | --- |
| Property expansion: address, contacts, timezone, currency, tax rate, amenities, stay times | Client-specific fields — those come from real client conversations |
| Room types, physical spaces, bed-level units, and the mapping that lets one room sell two ways | Housekeeping status and turnover queues — roadmap R3 |
| Allocation with a database-enforced concurrency boundary (roadmap §5) | Overbooking allowances, channel sync — R4 |
| Availability read path with the sweep-line algorithm (roadmap §5.1) | Availability caching in Redis — the trigger is in phase-4-completion §2, and there is no measured hot path yet |
| Booking lifecycle: create, modify, cancel, check in, check out, no-show | Payments and a payment gateway — roadmap R2. `amount_paid_minor` is a number a human types |
| Date-range pricing: a rate plan per room type, a per-date rate calendar, a base rate fallback | Dynamic pricing / revenue management — roadmap §8, explicitly not built |
| Guest records with the PII discipline roadmap §6 requires | Form C / MRZ capture — roadmap R3 §6.1, and it needs a guest record to attach to, which this phase creates |
| Repeatable tenant provisioning, administrative only | **Self-serve signup** — premature before real client volume |
| Experiences / retreat bundles | Deliberately deferred. See §13 |

---

## 2. Property expansion — V6

`property` is `name`, `slug`, `tenant_id`, `created_at`. Everything a property record needs to be
*usable* is missing, and two of the missing fields are load-bearing for correctness rather than
presentation.

**The timezone is the important one.** Every business-day boundary in a PMS is property-local: which
date "tonight" is, when a night rolls over, whether a booking arriving at 01:00 is today's arrival or
yesterday's. A PMS that computes those in UTC is wrong by up to a day for half the world, and the
bug appears as "the arrivals list is empty at 6am." Store an IANA zone id, validate it against
`ZoneId.getAvailableZoneIds()` at the API boundary, and derive every business date from it.

**Money is the other.** Amounts are `bigint` **minor units** (paise, cents) plus an ISO 4217 code —
never floating point, never a bare number whose currency is implied. The currency lives on the
property because a property transacts in one currency; a booking copies it at creation so a later
property edit cannot silently restate an old booking's total.

```sql
alter table property
    add column legal_name     text,
    add column description    text,
    add column status         text not null default 'ACTIVE'
                                   check (status in ('ACTIVE', 'INACTIVE')),
    add column timezone       text,        -- IANA, e.g. 'Asia/Kolkata'. NOT NULL after backfill
    add column currency_code  char(3),     -- ISO 4217.                  NOT NULL after backfill
    add column country_code   char(2),     -- ISO 3166-1 alpha-2
    add column address_line1  text,
    add column address_line2  text,
    add column city           text,
    add column state_region   text,
    add column postal_code    text,
    add column contact_email  text,
    add column contact_phone  text,
    add column check_in_time  time not null default '14:00',
    add column check_out_time time not null default '11:00',
    add column tax_rate_bps   integer not null default 0
                                   check (tax_rate_bps between 0 and 10000);
```

`timezone` and `currency_code` get **no default**, for the same fail-fast reason `GOOGLE_API_KEY`
and the three `ALTSTAY_DB_*` values get none: a defaulted timezone is a wrong answer that looks like
a right one. They are added nullable, backfilled once for the rows that already exist, then set
`not null` in the same migration — see §11 for the RLS trap that makes that backfill harder than it
looks.

`tax_rate_bps` is a **single flat rate in basis points**, applied to the booking total. India's GST
on accommodation is tariff-slabbed and the slabs move; modelling that properly is client- and
regulation-specific and belongs in a conversation with a real property, not in this plan. One rate
is enough to produce a correct-looking total for a pilot, and the field is where slab logic attaches
when there is a reason for it. **Written trigger to build slabs: the first property whose tariffs
straddle a slab boundary.**

### 2.1 Amenities

A controlled vocabulary, not free text — free text cannot be filtered on, and every property spells
"Wi-Fi" differently.

```sql
create table amenity (           -- reference data. No tenant_id, no RLS, no PII. See §11.2.
    code     text primary key,   -- 'WIFI', 'BREAKFAST', 'LOCKERS', 'AC', 'LAUNDRY', …
    label    text not null,
    category text not null       -- 'CONNECTIVITY' | 'FOOD' | 'FACILITY' | 'SERVICE'
);

create table property_amenity (  -- tenant-scoped, RLS as V4
    tenant_id    uuid not null references tenant (id) on delete cascade,
    property_id  uuid not null references property (id) on delete cascade,
    amenity_code text not null references amenity (code),
    primary key (property_id, amenity_code)
);
```

Seeded with roughly twenty codes covering what a hostel actually advertises. Rejected alternative: a
`text[]` column with a check constraint — cheaper to write, but the vocabulary then has no labels, no
categories, and no way to render a filter UI without hardcoding the list in two languages of code.

---

## 3. Inventory — V7

Roadmap §5 is an irreversible architecture decision and this section implements it rather than
revisiting it. Its sketch, restated:

```
Property
└── Space              a physical room, capacity N
     ├── sale modes:   WHOLE | PER_UNIT   (which are enabled)
     └── Unit[]        the individual beds
```

with the crux being that **one physical room can be sold two mutually exclusive ways**, and the two
must never be bookable at once.

### 3.1 Three tables and one join, and why the join is not optional

```sql
create table room_type (              -- the sellable class: what a guest chooses
    id            uuid primary key default gen_random_uuid(),
    tenant_id     uuid not null references tenant (id) on delete cascade,
    property_id   uuid not null references property (id) on delete cascade,
    code          text not null,      -- 'DORM6MIX', 'PRIV2'
    name          text not null,      -- '6-bed mixed dorm'
    sale_mode     text not null check (sale_mode in ('PER_UNIT', 'WHOLE')),
    kind          text not null check (kind in ('DORM', 'PRIVATE')),
    max_occupancy integer not null check (max_occupancy > 0),
    base_rate_minor bigint not null check (base_rate_minor >= 0),
    description   text,
    is_active     boolean not null default true,
    created_at    timestamptz not null default now(),
    unique (tenant_id, property_id, code)
);

create table space (                  -- a physical room
    id          uuid primary key default gen_random_uuid(),
    tenant_id   uuid not null references tenant (id) on delete cascade,
    property_id uuid not null references property (id) on delete cascade,
    name        text not null,        -- '101', 'Sea View Dorm'
    floor       text,
    is_active   boolean not null default true,
    created_at  timestamptz not null default now(),
    unique (tenant_id, property_id, name)
);

create table unit (                   -- a bed
    id         uuid primary key default gen_random_uuid(),
    tenant_id  uuid not null references tenant (id) on delete cascade,
    space_id   uuid not null references space (id) on delete cascade,
    label      text not null,         -- '101-A', '101-C-top'
    unit_kind  text not null check (unit_kind in ('SINGLE', 'BUNK_TOP', 'BUNK_BOTTOM', 'DOUBLE')),
    is_active  boolean not null default true,
    created_at timestamptz not null default now(),
    unique (tenant_id, space_id, label)
);

create table room_type_space (        -- which spaces can serve which sellable class
    tenant_id    uuid not null references tenant (id) on delete cascade,
    room_type_id uuid not null references room_type (id) on delete cascade,
    space_id     uuid not null references space (id) on delete cascade,
    primary key (room_type_id, space_id)
);
```

**`room_type_space` is what makes the hybrid case expressible.** Space 101 belongs to *both* the
`DORM6MIX` room type (`PER_UNIT`) and the `PRIV6` room type (`WHOLE`). A guest buying a dorm bed and
a guest buying the whole room are buying two different products backed by the same six beds.

*Rejected alternative:* `space.room_type_id`, a single foreign key — the shape most hotel PMSes use
and the reason roadmap §1 says owners run the hybrid case in a spreadsheet. It cannot express one
room sellable two ways, which is the entire crux of §5. Recorded so it is not reintroduced as a
"simplification."

**`kind` is not redundant with `sale_mode`.** A whole-dorm buyout for a group is `sale_mode = WHOLE`
and `kind = DORM`; a private double is `WHOLE` and `PRIVATE`. `sale_mode` decides how capacity is
consumed; `kind` is what the guest thinks they are buying.

### 3.2 Capacity is derived, never stored

A space's capacity is `count(unit where space_id = … and is_active)`. It is deliberately **not** a
column. A stored capacity is a second source of truth, and the day it disagrees with the bed list is
the day the availability numbers stop being trustworthy for a reason nobody can find. A private
double is two units; a six-bed dorm is six.

Consequence: **every space must have at least one unit.** Enforced by the service on creation and by
the integrity test in §12.3, not by a trigger — a per-row trigger cannot see the final state of a
batch insert without deferred constraints, and a deferred constraint here is more machinery than the
invariant is worth.

---

## 4. The concurrency boundary — V8

Roadmap §5.1 is unambiguous about the division of labour, and it is worth restating because getting
it backwards is the classic failure:

> exclusion constraint for correctness, sweep-line for the read path, and neither one can do the
> other's job.

### 4.1 One constraint covers both sale modes, because everything allocates at the bed

**The design move that makes this work: a `WHOLE` booking allocates every active unit in the space.**
A private-room booking takes all six unit rows for those dates; any dorm-bed booking on those dates
then collides on the same constraint. Property 1 of §5 — *"allocating a Unit blocks whole-Space sale
for those dates, and vice versa"* — falls out of one index instead of out of application logic that
has to remember both directions.

```sql
create table allocation (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid not null references tenant (id) on delete cascade,
    unit_id         uuid not null references unit (id),
    booking_line_id uuid not null references booking_line (id) on delete cascade,
    check_in        date not null,
    check_out       date not null check (check_out > check_in),
    -- Half-open [check_in, check_out): the checkout day IS bookable by the next guest.
    -- roadmap §5.1: "boundary handling is exactly where this goes wrong."
    stay_range      daterange generated always as
                        (daterange(check_in, check_out, '[)')) stored,
    released_at     timestamptz,
    created_at      timestamptz not null default now(),

    constraint allocation_no_overlap
        exclude using gist (unit_id with =, stay_range with &&)
        where (released_at is null)
);
```

Three deliberate choices in that DDL:

- **A generated column, not a `daterange` Hibernate has to map.** Hibernate has no mapping for
  `daterange`, and `ddl-auto: validate` only checks that *mapped* columns exist — extra columns are
  ignored. So the entity maps `check_in` and `check_out` as ordinary `LocalDate`s and never mentions
  `stay_range`, while the constraint operates on the generated value. **Verify the generated column
  is accepted:** stored generated columns require an `IMMUTABLE` expression, and if this PostgreSQL
  build does not treat the three-argument `daterange` constructor as immutable, the fallback is a
  `before insert or update` trigger maintaining the column. Check it before writing the entity.
- **A partial exclusion constraint, `where (released_at is null)`.** Cancelling releases the bed
  without deleting the row, so "which bed was that guest in" survives a cancellation. Deleting the
  rows would be simpler and would lose the operational history that makes a PMS a system of record.
- **`check_out > check_in`.** A zero-night allocation is meaningless and an empty range silently
  overlaps nothing, so it would pass the exclusion constraint and consume no inventory.

### 4.2 Pre-flight: `btree_gist`, and what to do if it is not available

The constraint mixes an equality operator on `uuid` with an overlap operator on `daterange` in one
GiST index, which requires the **`btree_gist`** extension. Two things must be established *before*
V8 is written, and neither can be assumed:

```sql
select name, default_version, installed_version
from pg_available_extensions where name = 'btree_gist';

create extension if not exists btree_gist;   -- run as altstay_app, not as neondb_owner
```

The second one is the real question. Flyway runs as `altstay_app`, a deliberately unprivileged role
(§1.3 of [phase-4-foundations.md](phase-4-foundations.md)), and `CREATE EXTENSION` is not something
every managed provider grants to an ordinary role. If it fails there are two ways out, in order of
preference:

1. **`neondb_owner` creates the extension once**, out of band, and V8 assumes it. This puts one
   schema object outside the migrations, which §3.2 already judged a worse trade than a PII-free
   table — but for an extension, unlike a function, there is no ownership consequence and the
   migration can assert its presence and fail loudly if it is missing.
2. **The per-night spine.** Drop `daterange` entirely; one `allocation_night (unit_id, stay_date)`
   row per bed per night, with `unique (unit_id, stay_date)` doing the job the exclusion constraint
   did. It needs no extension, the enforcement is still in the database, availability becomes a
   `group by stay_date`, and the cost is row count — a 40-bed property is ~14,600 rows a year, which
   is nothing. It is genuinely a defensible design and several production PMSes use it.

Roadmap §5's decision was *"enforce it in the database … not in application logic"*, and both
options honour that; only the mechanism differs. **This pre-flight is step 1 of §13 and blocks V8.**

### 4.3 What the constraint does not do

RLS does not participate in unique or exclusion constraint checks — the index is global. That is not
a leak here, because `unit_id` is a UUID owned by exactly one tenant, so a cross-tenant collision
cannot occur. `tenant_id` is on the row for policy purposes, not for the constraint. Worth knowing
before someone "fixes" the constraint by adding `tenant_id with =` to it, which would weaken nothing
and buy nothing.

---

## 5. Guests and bookings — V9

Industry-standard shape. A booking is a header plus lines; lines are what was sold; allocations are
which physical beds serve them.

```sql
create table guest (
    id           uuid primary key default gen_random_uuid(),
    tenant_id    uuid not null references tenant (id) on delete cascade,
    full_name    text not null,
    email        text,
    phone        text,
    country_code char(2),
    date_of_birth date,
    notes        text,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);
create index guest_tenant_email_idx on guest (tenant_id, lower(email));

create table booking (
    id                 uuid primary key default gen_random_uuid(),
    tenant_id          uuid not null references tenant (id) on delete cascade,
    property_id        uuid not null references property (id) on delete cascade,
    reference          text not null,          -- 'ALT-7K2QD9', shown to the guest
    guest_id           uuid not null references guest (id),
    status             text not null check (status in
                         ('BOOKED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW')),
    source             text not null check (source in ('DIRECT', 'WALK_IN', 'PHONE', 'OTA')),
    check_in           date not null,
    check_out          date not null check (check_out > check_in),
    adults             integer not null default 1 check (adults >= 1),
    children           integer not null default 0 check (children >= 0),
    currency_code      char(3) not null,       -- copied from property at creation
    subtotal_minor     bigint not null check (subtotal_minor >= 0),
    tax_minor          bigint not null default 0 check (tax_minor >= 0),
    total_minor        bigint not null check (total_minor >= 0),
    amount_paid_minor  bigint not null default 0 check (amount_paid_minor >= 0),
    payment_state      text not null default 'UNPAID'
                            check (payment_state in ('UNPAID', 'PARTIAL', 'PAID')),
    idempotency_key    text,
    notes              text,
    created_by         uuid references app_user (id) on delete set null,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    cancelled_at       timestamptz,
    cancellation_reason text,
    unique (tenant_id, reference)
);
create unique index booking_tenant_idempotency_key
    on booking (tenant_id, idempotency_key) where idempotency_key is not null;

create table booking_line (
    id           uuid primary key default gen_random_uuid(),
    tenant_id    uuid not null references tenant (id) on delete cascade,
    booking_id   uuid not null references booking (id) on delete cascade,
    room_type_id uuid not null references room_type (id),
    space_id     uuid references space (id),    -- set for WHOLE lines, null for PER_UNIT
    check_in     date not null,
    check_out    date not null check (check_out > check_in),
    unit_count   integer not null default 1 check (unit_count >= 1),
    amount_minor bigint not null check (amount_minor >= 0),
    created_at   timestamptz not null default now()
);

create table booking_status_history (
    id          uuid primary key default gen_random_uuid(),
    tenant_id   uuid not null references tenant (id) on delete cascade,
    booking_id  uuid not null references booking (id) on delete cascade,
    from_status text,
    to_status   text not null,
    changed_by  uuid references app_user (id) on delete set null,
    reason      text,
    changed_at  timestamptz not null default now()
);
```

**PII.** `guest` is the first table in this system holding personal data, and roadmap §6's DPDP rules
attach the moment it exists: never logged, never in an error payload, **never in an LLM prompt**, and
subject to a retention window whose deletion path must actually run (R3). The Track E logging test
(phase-4-completion §3.4) gets a sibling asserting no guest name, email or phone appears in a log
line.

**`amount_paid_minor` and `payment_state` are numbers a human types.** There is no gateway, no
webhook, no reconciliation — those are roadmap R2, and pretending otherwise here would be the exact
"payment flow that is correct under retries" story told without the flow. A front desk needs to know
whether a guest has paid; that is all this is.

**`reference`** is generated as `ALT-` plus six characters from an unambiguous alphabet (no `0/O`,
no `1/I/l`) — it gets read aloud over a phone. Collisions are handled by retrying against
`unique (tenant_id, reference)`.

**`idempotency_key`** is optional and set by the caller. A front desk double-clicking "Confirm" is a
real and frequent event; replaying the same key returns the original booking rather than creating a
second one. This is the light version of roadmap R2's idempotency work, not a substitute for it.

### 5.1 The status lifecycle

```
                ┌──────────────► CANCELLED
                │
BOOKED ─────────┼──────────────► NO_SHOW
   │            │
   └─► CHECKED_IN ─────────────► CHECKED_OUT
```

`CANCELLED`, `NO_SHOW` and `CHECKED_OUT` are terminal. `NO_SHOW` is in the set because a front desk
needs it nightly and because it releases inventory differently from a cancellation in reporting,
even though both release it.

The machine lives in **one place** — an enum with `canTransitionTo(BookingStatus)` — checked in the
service, backed by the `check` constraint on the column, and recorded in `booking_status_history` on
every transition with the acting user. An illegal transition is **409** with a problem type of
`.../invalid-booking-transition`, never a 500 and never a silent no-op.

Rules attached to the transitions:

- **Check-in** is permitted from the property-local date of `check_in` onward. Earlier is allowed but
  flagged as an early check-in in the response, because refusing it outright is how staff end up
  keeping a parallel notebook (roadmap R3's kill criterion).
- **Check-out before `check_out`** shortens the allocations to end today, in the same transaction, so
  the bed becomes sellable tonight. This is the single most commonly missed behaviour in a hostel
  PMS.
- **Cancel** and **no-show** set `released_at` on every allocation of the booking. The rows stay.
- **Modify dates or room type** releases and re-allocates **inside one transaction**. If the new
  dates conflict, the whole modification rolls back and the original allocation stands — the guest
  keeps the bed they had. That property is the reason it is one transaction rather than a
  release-then-book sequence.

### 5.2 Cancellation policy

Not modelled. A cancellation policy is a commercial term that differs per property and per rate plan,
and inventing one here would be exactly the "business-specific requirement" this plan is meant to
avoid. `cancellation_reason` is free text; refunds are outside the system because payments are.
**Written trigger:** the first design partner who asks for a non-refundable rate.

---

## 6. Rates and pricing — V10

Standard hospitality shape: a base rate on the room type, overridden per date by a rate calendar.

```sql
create table rate_plan (
    id            uuid primary key default gen_random_uuid(),
    tenant_id     uuid not null references tenant (id) on delete cascade,
    property_id   uuid not null references property (id) on delete cascade,
    room_type_id  uuid not null references room_type (id) on delete cascade,
    code          text not null,
    name          text not null,
    is_default    boolean not null default false,
    is_active     boolean not null default true,
    created_at    timestamptz not null default now(),
    unique (tenant_id, room_type_id, code)
);
create unique index rate_plan_one_default_per_room_type
    on rate_plan (room_type_id) where is_default;

create table rate_calendar (
    tenant_id    uuid not null references tenant (id) on delete cascade,
    rate_plan_id uuid not null references rate_plan (id) on delete cascade,
    stay_date    date not null,
    amount_minor bigint not null check (amount_minor >= 0),
    primary key (rate_plan_id, stay_date)
);
```

**One row per date, not a date range with an amount.** Per-date rows are how every channel manager in
the industry represents rates, they make "override just the 26th" a single upsert, and they remove
the ambiguity of overlapping ranges — which is a real bug class, not a hypothetical one. The API
takes a range and expands it; the storage stays flat. `rate_plan_one_default_per_room_type` makes
"which rate applies when the caller names none" a database fact rather than a convention.

**Quote arithmetic**, in one place, in integers:

```
for each night in [check_in, check_out):
    nightly = rate_calendar(plan, night) ?? room_type.base_rate_minor
    subtotal += nightly × unit_count
tax   = (subtotal × property.tax_rate_bps + 5000) / 10000     -- half-up, once, on the total
total = subtotal + tax
```

Rounding happens **once, on the total**, never per night — rounding each night and summing produces a
total that disagrees with the arithmetic a guest does on the invoice. The quote is a pure function of
(nights, rates, unit count, tax rate) and therefore unit-testable in the **offline** suite, which is
where its tests live.

A booking whose property currency differs from the rate plan's expectation is a 409, not a coerced
conversion. There is no FX in this system and there should not be one.

---

## 7. The availability read path

Roadmap §5.1 puts the algorithm here and is specific about why: *"which of these 40 beds are free on
which of the next 60 days, in both sale modes"* is asked on every calendar render, and the naive form
is a query per cell.

**Shape:**

```
GET /api/v1/properties/{slug}/availability?from=2026-09-01&to=2026-09-30[&roomTypeId=…]

{ "from": …, "to": …, "currency": "INR",
  "roomTypes": [
    { "roomTypeId": …, "code": "DORM6MIX", "saleMode": "PER_UNIT",
      "days": [ { "date": "2026-09-01", "availableUnits": 4, "totalUnits": 6, "rateMinor": 60000 }, … ] },
    { "roomTypeId": …, "code": "PRIV6", "saleMode": "WHOLE",
      "days": [ { "date": "2026-09-01", "availableSpaces": 0, "totalSpaces": 1, "rateMinor": 300000 }, … ] } ] }
```

**Algorithm.** One query returns every non-released allocation overlapping `[from, to)`, joined out to
its unit and space. A sweep line over the start and end events, ordered by date, maintains a running
count of occupied units per space; the merged interval set falls out in roughly `O((B + U) log B)`
plus output size rather than `O(units × days × bookings)`.

**The two sale modes are coupled, and that coupling is the part that is not in the textbook.**
Because a `WHOLE` sale allocates every unit in the space (§4.1), the coupling reduces to arithmetic
on the same occupied counts:

- `PER_UNIT` availability for a room type = sum over its spaces of `(activeUnits − occupiedUnits)`.
- `WHOLE` availability = the number of its spaces where `occupiedUnits = 0` for **every** date in the
  requested range — an intersection over the range, not a per-day count.

`AvailabilityCalculator` is a **pure function** — `(units by space, allocations, range) → result` —
with no repository, no clock and no database. That is a deliberate testability constraint: it means
the interesting algorithm is covered by the offline suite, and only the query that feeds it and the
constraint that guards writes need `ALTSTAY_DB_TESTS=true`.

Caching is not in scope. Roadmap §4.2 wants availability cached in Redis at R2; the trigger for that
is a measured hot path, and there is no traffic to measure yet.

---

## 8. Roles and permissions

Track B built three roles and one endpoint that uses them. This is the matrix the console needs.

| Capability | OWNER | MANAGER | FRONT_DESK |
| --- | --- | --- | --- |
| Create/edit property, amenities, tax rate | ✓ | — | — |
| Create/edit room types, spaces, units | ✓ | ✓ | — |
| Set rates, rate calendar | ✓ | ✓ | — |
| View availability and calendar | ✓ | ✓ | ✓ |
| Create / modify / cancel a booking | ✓ | ✓ | ✓ |
| Check in / check out / no-show | ✓ | ✓ | ✓ |
| Create / edit guest records | ✓ | ✓ | ✓ |
| Record a payment amount | ✓ | ✓ | ✓ |
| Edit the knowledge base | ✓ | ✓ | — |
| Manage users | ✓ | — | — |

Enforced with `@PreAuthorize` on the controller methods, following
`PropertyControllerTest.frontDeskRoleIsRefusedOnOwnerEndpoint`. Every `—` in that table is a test:
the role is refused with **403**, and the refusal is asserted, not assumed. The console hides what a
role cannot do, and hiding is **not** the enforcement — §12 requires the server-side test regardless
of what the UI renders.

---

## 9. API surface

Follows [phase-1-backend-ai.md](phase-1-backend-ai.md) §4: thin controllers, record DTOs, RFC 9457
`ProblemDetail` from `@RestControllerAdvice`, validation at the boundary. Every route below is
tenant-scoped through the existing aspect and reachable only with a session.

| Method | Path | Role | Notes |
| --- | --- | --- | --- |
| GET/PUT | `/api/v1/properties/{slug}` | any / OWNER | Expanded property record |
| GET | `/api/v1/amenities` | any | The reference vocabulary |
| GET/POST/PUT | `/api/v1/properties/{slug}/room-types` | MANAGER+ | |
| GET/POST/PUT | `/api/v1/properties/{slug}/spaces` | MANAGER+ | Units nested on create |
| POST/DELETE | `/api/v1/room-types/{id}/spaces/{spaceId}` | MANAGER+ | The hybrid mapping |
| GET | `/api/v1/properties/{slug}/availability` | any | §7 |
| GET/PUT | `/api/v1/rate-plans/{id}/calendar` | MANAGER+ | PUT takes a range, expands to rows |
| POST | `/api/v1/bookings/quote` | any | Priced, allocates nothing |
| GET/POST | `/api/v1/bookings` | any | List is filterable by date, status, guest, reference |
| GET/PATCH | `/api/v1/bookings/{reference}` | any | PATCH modifies dates/lines |
| POST | `/api/v1/bookings/{reference}/transitions` | any | `{ "to": "CHECKED_IN", "reason": … }` |
| GET | `/api/v1/properties/{slug}/front-desk` | any | Arrivals, departures, in-house for a date |
| GET/POST/PUT | `/api/v1/guests` | any | PII rules apply |

New problem types, all under `https://api.altstay.com/errors/`:
`no-availability` (409), `booking-conflict` (409, the exclusion constraint fired),
`invalid-booking-transition` (409), `rate-currency-mismatch` (409), `unknown-room-type` (404).

A booking creation that loses the race must return **409 with a message a human can act on**, not a
constraint violation stack. That mapping is a test, because the raw Postgres error is the default and
the default leaks the schema.

---

## 10. Tenant provisioning

Today a tenant exists only if someone writes the INSERTs by hand; the only working example is
`AuthLoginIT.seedTenant` in the test sources. That is not a process.

**Decision: an `ApplicationRunner` behind a `provision` Spring profile. Not an HTTP endpoint, and
explicitly not self-serve signup.**

```powershell
$env:ALTSTAY_PROVISION_OWNER_PASSWORD = "…"   # no default; never echoed, never logged
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=provision" `
  "-Dspring-boot.run.arguments=--altstay.provision.tenant-slug=driftwood --altstay.provision.tenant-name=Driftwood Beach Hostel --altstay.provision.owner-email=owner@example.com --altstay.provision.property-name=Driftwood Goa"
```

Why a runner rather than an internal endpoint: §3.5 requires provisioning to be *"unreachable from
any request-scoped path"*, and a runner is unreachable by construction rather than by an
authorization rule that has to stay correct. There is no admin token to leak and no new route to
forget to protect.

**The mechanism is already proven.** V4's `tenant` policy has
`with check (id = app_current_tenant())`, so a new tenant cannot be inserted by a connection bound to
a different tenant — but it *can* by one bound to the id being created. Generate the UUID in
application code, bind `app.tenant_id` to it, then insert. `TenantIsolationIT.seedTenant` does
exactly this and passes.

What one run does, in one transaction:

1. Create the `tenant`; the V5 trigger fills `tenant_directory` so the slug is loginable.
2. Create the owner `app_user` with a bcrypt hash of the supplied password, and its `OWNER`
   `user_role`.
3. Create the first `property` with a timezone and currency taken from arguments, both required.
4. Print the tenant id, the slug and the owner email. **Never the password**, which came from the
   environment and is never written to a log, a file, or the console.

Re-running with an existing slug fails on `tenant.slug`'s unique constraint with a clear message,
rather than half-creating anything. Configuration binds through a
`@ConfigurationProperties("altstay.provision")` **record**, `@Validated`, with no defaults — the same
fail-fast rule the datasource and API key follow.

Room types, spaces and units are **not** provisioned. They are set up in the console (Phase 6 §2.6),
because that path has to work anyway and a seeding shortcut is how it stays untested.

---

## 11. Migration mechanics, and the two traps in them

### 11.1 A backfill under `FORCE ROW LEVEL SECURITY` silently writes nothing

V4's closing note says it and V5 already had to work around it:

> a later migration that backfills or seeds tenant-scoped rows will be filtered by these policies
> like any other statement … Silent zero-row backfills are the failure mode to watch for.

V6's `timezone` / `currency_code` backfill runs into this directly. Follow V5's pattern — disable
RLS on the table, backfill, re-enable **and re-force** — and then **assert the row count**, because
`update … set timezone = 'Asia/Kolkata'` reports success whether it touched two rows or zero. The
migration ends with a check that no `property` row has a null timezone before it adds the `not null`,
so a silent zero-row backfill fails the migration instead of passing it.

### 11.2 A new table with no policy is not protected — it is public

RLS is per-table. A tenant-scoped table added without `enable`, `force` and a policy is readable by
every tenant, and it looks completely normal. This phase adds **nine** such tables, which is nine
chances to forget.

**Therefore: `SchemaTenancyIT` enumerates every table in the `public` schema and asserts
`relrowsecurity` and `relforcerowsecurity` are both true**, with a small allowlist that has to be
edited deliberately:

| Allowlisted table | Why it is exempt |
| --- | --- |
| `tenant_directory` | Deliberately unprotected, no PII — §3.2 of phase-4-foundations |
| `amenity` | Reference vocabulary, no tenant column, no PII |
| `flyway_schema_history` | Not business data |

This is the cheapest test in the plan and the one most likely to catch a real incident. It fails the
build the day someone adds a table and forgets the four lines.

### 11.3 The rest

- Migrations are `V6` … `V10` in `backend/src/main/resources/db/migration/`, applied by Flyway as
  `altstay_app`. `spring-boot-flyway` is already a dependency — without it Flyway sits on the
  classpath and never runs.
- `ddl-auto: validate` means every new entity must match its migration exactly. `char(3)` and
  `char(2)` map to `String` with `columnDefinition`; `date` to `LocalDate`; `time` to `LocalTime`;
  `timestamptz` to `OffsetDateTime`, as `Property` already does.
- DTOs are records and Lombok never touches them. Entities keep the `@Getter/@Setter` style
  `Property` uses.
- Every new service is `@TenantScoped` and `@Transactional`; the aspect throws without an active
  transaction, so a repository call outside one fails immediately rather than reading zero rows.
- Every new bean is `@ConditionalOnProperty(name = "spring.datasource.url")`, and every `@WebMvcTest`
  slice for it declares that property (§3.7 finding 6).

---

## 12. Testing strategy

### 12.1 What runs offline, and why that split matters

The offline invariant is not negotiable: `mvnw clean verify` must stay green with `GOOGLE_API_KEY`
and all three `ALTSTAY_DB_*` unset. That constrains the *design*, not just the tests — it is why the
availability calculator, the quote arithmetic and the status machine are pure functions.

| Offline, always runs | `ALTSTAY_DB_TESTS=true` only |
| --- | --- |
| `AvailabilityCalculatorTest` — sweep line vs a brute-force oracle | `BookingConcurrencyIT` — the race for the last bed |
| `QuoteCalculatorTest` — rounding, multi-night, tax, unit counts | `AllocationConstraintIT` — the constraint itself, both sale modes |
| `BookingStatusMachineTest` — every legal and illegal transition | `SchemaTenancyIT` — §11.2 |
| Controller slices with mocked services, including every 403 in §8 | `BookingLifecycleIT` — create → check in → early check-out → rebook the freed night |
| Reference/DTO validation | `TenantProvisioningIT` — provision, then log in over HTTP as the created owner |

### 12.2 The two tests roadmap §5.1 asks for by name

**The concurrency test.** `BookingConcurrencyIT`: one bed left, eight threads booking it
simultaneously, asserting **exactly one** 201 and seven clean 409s, and that
`select count(*) from allocation where unit_id = … and released_at is null` is 1.

> That single test is more persuasive than any amount of describing the design.

And it must be **watched failing first**, the way `TenantIsolationIT` and `TenantBindingIT` were:
drop `allocation_no_overlap`, run it, record that more than one thread succeeded, restore the
constraint. A green concurrency test that has never been red proves that the threads did not
actually race.

**The randomized oracle.** `AvailabilityCalculatorPropertyTest`: a seeded `Random` generates a few
hundred random unit/allocation sets, and the sweep-line result is compared against a naive
day-by-day count. The seed is printed on failure so a failure is reproducible. Hand-rolled rather
than jqwik — one more dependency for a loop and a seed is not a trade this repo makes (roadmap §10),
and the written trigger to adopt a real property-testing library is the second place that needs one.

Boundary cases that get their own named tests because half of all PMS bugs live there: a booking
ending the day another begins (must **not** conflict), a one-night booking, a booking spanning a
month boundary, and a whole-space booking colliding with a single bed in that space.

### 12.3 Integrity

`InventoryIntegrityIT` asserts the invariants that have no constraint: every active space has at
least one active unit, every `room_type_space` row joins a room type and space in the same property,
and no `booking` has lines whose date range falls outside its own.

---

## 13. Deliberately not in this phase

Each of these will be tempting while the code is open. Each has a written trigger instead.

- **Experiences / retreat bundles.** Roadmap §5 is explicit that *"a retreat is a bundle, not a room
  type"* and that modelling it as one is the hack every incumbent makes. It is genuinely part of the
  differentiator — and it is a second product concept layered on top of bed-nights, which is
  materially easier once bed-nights are proven. **Trigger: the inventory and booking DoD below is
  closed, or a design partner sells a retreat.** Building it alongside the base model doubles the
  surface of the hardest part of this phase.
- **The concierge answering availability.** Withheld until the gate — §0.
- **Payments, refunds, invoices, folios.** Roadmap R2.
- **Housekeeping, drag-and-drop bed assignment, Form C.** Roadmap R3.
- **Channel sync, OTA distribution.** Roadmap R4.
- **Availability caching, Redis.** Triggers already written — phase-4-completion §2.
- **Self-serve signup, in-product billing.** Premature before real client volume.

---

## 14. Sequence

1. **Pre-flight `btree_gist`** (§4.2). It decides V8's shape and nothing else can start on top of a
   guess.
2. **V6 property expansion** + the backfill assertion (§11.1), and `SchemaTenancyIT` (§11.2) —
   written now, while there are two tables to add rather than nine.
3. **V7 inventory** + the room-type/space mapping, with the console-less API and its tests.
4. **V8 allocation** + `AllocationConstraintIT`, **watched failing first**.
5. **V9 guests and bookings** + the status machine and its offline tests.
6. **V10 rates** + the quote calculator, offline.
7. **Availability read path** (§7) — the calculator offline, the query DB-gated.
8. **`BookingConcurrencyIT`** (§12.2), watched failing first.
9. **Provisioning runner** (§10) + `TenantProvisioningIT`.
10. Hand off to [phase-6-staff-console.md](phase-6-staff-console.md).

Steps 1–4 before anything else. The concurrency boundary is this phase's equivalent of Phase 4's
tenancy model: the one decision that is expensive to get wrong and cheap to prove.

---

## 15. Definition of Done

```powershell
cd backend; .\mvnw.cmd clean verify
```

```powershell
$env:ALTSTAY_DB_TESTS = "true"; cd backend; .\mvnw.cmd clean verify
```

- [ ] `mvnw clean verify` is green **offline, with `GOOGLE_API_KEY` and all three `ALTSTAY_DB_*`
      unset and `backend/.env.properties` moved aside** — the availability calculator, the quote
      arithmetic and the status machine all run in this pass
- [ ] `mvnw clean verify` with `ALTSTAY_DB_TESTS=true` is green end to end, every new IT included
- [ ] **`BookingConcurrencyIT` was watched failing first** — with `allocation_no_overlap` dropped,
      more than one thread books the last bed; the recorded output is pasted into this section, the
      way §9 of [phase-4-foundations.md](phase-4-foundations.md) records `TenantIsolationIT`'s
- [ ] A booking ending on the day another begins does **not** conflict — the checkout-day boundary,
      asserted
- [ ] A whole-space booking and a single-bed booking in that space **cannot both** hold the same
      night, in either order of arrival
- [ ] `SchemaTenancyIT` passes, and **fails** when RLS is dropped from any one new table
- [ ] The V6 backfill asserts its own row count; a zero-row backfill fails the migration
- [ ] Every `—` in §8's role matrix is a 403 with a test behind it
- [ ] `AvailabilityCalculatorPropertyTest` matches a brute-force oracle over ≥200 seeded random cases
- [ ] Provisioning is verified by command, end to end, with no hand-written SQL:

  ```powershell
  $env:ALTSTAY_PROVISION_OWNER_PASSWORD = "…"; cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=provision" "-Dspring-boot.run.arguments=--altstay.provision.tenant-slug=demo …"
  ```

  ```powershell
  curl.exe -i -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{\"tenantSlug\":\"demo\",\"email\":\"owner@example.com\",\"password\":\"…\"}'
  ```

  200, a `JSESSIONID` cookie, and `OWNER` in the response
- [ ] A full walkthrough runs against a provisioned tenant and is added to
      [dev-runbook.md](dev-runbook.md) as §7: create a property → two room types sharing one space →
      six units → rates for a week → query availability → book a dorm bed → observe the whole-room
      product disappear for those dates → check in → check out early → observe the freed night become
      bookable again → cancel a second booking → observe its bed return
- [ ] No guest name, email or phone appears in any log line — the Track E test, extended
- [ ] `ChatService`, `ChatController`, `concierge-system.st` and everything under
      `frontend/src/components` are **untouched** by this phase: `git diff --stat` proves it
- [ ] §9.1's three constraints still hold, and nothing in this phase is cited as evidence for the R0
      gate

Phase 5 is done when two guests cannot be sold the same bed, and that is proven by a test that was
watched failing.
