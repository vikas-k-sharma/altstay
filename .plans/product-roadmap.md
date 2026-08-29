# AltStay OS — Product Roadmap to a Production SaaS

Primarily a product-management view. The question it answers is not "what should we build next"
but **"what do we need to learn next, and what is the smallest thing that teaches us."**
Step-by-step engineering plans live in the per-phase files; this document sets what those phases
are *for*, and what evidence lets us move on.

It also carries the architecture decisions that are **expensive or impossible to reverse later** —
multi-tenancy and datastore choice (§4), the inventory model and its concurrency boundary (§5),
and the compliance data path (§6). Those sit here rather than in a phase plan because they are
release-gating and cross-cutting, not implementation detail.

Sections §3.1, §4.2, §5.1 and §6.1 additionally address this project's second audience: it is the
portfolio artifact for a Senior Full-Stack transition. Where the product goal and the interview
goal diverge, the divergence is named rather than glossed over — and it does diverge, most
sharply in §4.2.

---

## 1. The thesis

Alternative accommodation — hostels, surf camps, retreat centres, co-living stays — is a large,
fragmented, badly-served software market. The incumbents (Cloudbeds, eZee, Hotelogix, Little
Hotelier) are built on a hotel's mental model: *a room is the unit, a guest books a room, the
front desk runs a shift*. Almost none of that holds for a 40-bed hostel in Goa that also sells a
7-day yoga retreat and rents scooters.

Two specific mismatches are the opening:

1. **Inventory doesn't fit the schema.** The same physical room is a 6-bed dorm on Tuesday and a
   private double on Saturday. A retreat is a dated bundle of bed-nights plus non-inventory
   items. Hotel PMSes model this with hacks; owners run the hacks in a spreadsheet.
2. **The guest channel is WhatsApp, not email.** In India and South-East Asia, guests message
   before, during, and after their stay. The PMS has no presence there, so the owner is the
   integration — personally, at 2 AM.

**Where the money is:** hostels pay 15–18% commission to Hostelworld and Booking.com. On a
₹40L/year property that's ₹6–7L. Any credible shift of bookings from OTA to direct is worth an
order of magnitude more than a software subscription. **We do not sell a PMS. We sell recovered
commission, and the PMS is how we earn the right to it.** That single reframe drives the ordering
below — it's why the booking engine comes before the housekeeping module, which is not the order
a feature-list roadmap would produce.

---

## 2. The uncomfortable truth about the wedge

The AI concierge is an excellent *wedge* and a poor *product*.

Excellent wedge: it demos in thirty seconds, needs zero data migration (the objection that kills
most PMS switches), attaches to a pain the owner feels personally and nightly, and it's cheap to
build.

Poor product: any competent team clones it in a fortnight, a general-purpose assistant will
eventually do it adequately for free, and it produces no data the owner can't walk away from. A
concierge that only answers questions has **no retention mechanism**.

So the roadmap is a deliberate ladder out of the wedge, and each rung must be climbed before the
one below it commoditizes:

| Rung | What we do | What we earn | Switching cost |
| --- | --- | --- | --- |
| 1 | Answer guest questions | Trust, and the owner's attention | ~zero |
| 2 | Answer *availability* questions | A data relationship — we must know inventory | low |
| 3 | Take the booking + payment | A place in the revenue path | medium |
| 4 | Run the stay (calendar, check-in, compliance) | System of record | **high** |
| 5 | Distribute to OTAs | Being the thing they cannot unplug | **very high** |

Rung 2 is the pivotal one. The moment the concierge must answer "do you have a bed for Friday?",
it needs real inventory — and getting inventory into our system *is* onboarding onto the PMS,
smuggled in behind a feature the owner actually wants. That's the whole strategy in one sentence.

---

## 3. Releases

Each release has a hypothesis, a metric, and a **kill criterion**. The kill criteria are the
important part and the part usually skipped: they are what stop this becoming an eighteen-month
build for nobody.

### 3.1 The same releases, read as an engineering portfolio

This project has a second, legitimate audience: it is the artifact that carries a Senior
Full-Stack interview. Those two goals mostly agree — but where they diverge, the divergence is
worth naming rather than pretending it doesn't exist.

| Release | Product goal | Engineering substance to build deliberately |
| --- | --- | --- |
| **R0** | Prototype validation | Spring AI `ChatClient`, prompt assembly, Next.js state + BFF. **Strictly single-tenant** — resist every urge to generalize |
| **R1** | Real guests on WhatsApp | Webhook security (signature verification, replay defence), **idempotency under provider retries**, tenant resolution from inbound phone number, Postgres RLS, Redis rate limiting and per-tenant budgets |
| **R2** | Inventory + booking = the moat | The hybrid inventory model, **interval algorithms on the availability read path** (§5.1), concurrency correctness under contention, then idempotent booking writes and payment-provider webhook reconciliation |
| **R3** | Run the stay, incl. compliance | **Gemini Vision MRZ extraction with deterministic check-digit verification** (§6.1), secure PII lifecycle, browser camera capture, bed-assignment calendar |
| **R4** | Distribution | Multi-channel inventory sync, eventual consistency, conflict resolution, overbooking defence |

Three notes on this table, because they're the parts that most often go wrong:

**R1's WhatsApp webhook is what forces multi-tenancy — it is not deferrable past R1.** Inbound
messages for every property arrive at *one* endpoint; deciding which property a message belongs
to *is* tenant resolution. If R1 ships with real properties and persisted transcripts before RLS
exists, R2 has to retrofit tenant isolation onto a live dataset containing real guest PII. That
is the single most expensive migration in this entire document, and it is entirely avoidable by
doing §4 first.

**Don't drop the booking rung.** It is tempting to jump from inventory straight to the Form C
showcase, because Form C demos better. But §2's ladder only reaches a business at rung 3, and
payments happen to be excellent interview material too: idempotency keys, webhook ordering,
partial failures, and money that must reconcile are the problems senior interviews actually probe.
"I built a payment flow that is correct under retries" outranks another AI feature.

**Form C can move earlier if the design partners want it.** §6 argues it may be a stronger
*activation* wedge than booking, because it is legally mandatory rather than merely useful. If
R1's partners host foreign guests and ask for it, pulling a lightweight version into R2 is a
defensible product call. What is *not* defensible is building it before inventory exists —
compliance without a guest record to attach it to is a demo, not a feature.

---

### R0 — Concierge Prototype *(current — Phases 1–3)*

**Hypothesis:** Owners will let an AI answer their guests, if they control what it knows.

**Scope:** Phases 1–3 as planned. Local only, one property, knowledge base in the request body.

**Metric:** Do the two beta-test hostel owners, unprompted, ask when they can have it?

**Kill criterion:** Both testers' reaction is "cute, but I'd still want to answer myself." That's
a trust ceiling, not a feature gap, and no amount of building fixes it. Pivot to owner-facing
tooling (the ops side) instead of guest-facing.

**Watch for:** the owner who says *"can it also tell them if we have space?"* — that's rung 2
pulling itself forward, and it's the strongest possible signal.

---

### R1 — Design Partner Alpha

**Hypothesis:** The concierge holds up against real guests, on real WhatsApp, without supervision.

Everything in R0 is a demo. R1 is the first time the system meets people who didn't agree to be
impressed.

**Scope:**
- **Multi-tenancy from the first line of code** (see §4 — this is the one thing that is
  ruinously expensive to retrofit)
- Auth + roles: owner, manager, front desk
- Knowledge base moves to Postgres; versioned, with an edit history
- WhatsApp Cloud API webhooks replacing the mock UI
- Server-side conversation state — closes the injection hole in
  [phase-1-review.md](phase-1-review.md) finding #8
- Human handoff: escalation actually notifies a person, and they can take over the thread
- Per-tenant token budgets and rate limits
- Basic ops: structured logs, error tracking, uptime alerting

**Design partners:** 3–5 properties. Not more. Signed, engaged, and talking to you weekly.

**Metrics:**
- **Containment rate** — % of guest messages resolved with no human intervention. *The* number.
- **Escalation precision** — of messages it escalated, how many genuinely needed a human. Cheap
  to game by escalating everything; track alongside containment or it means nothing.
- **Hallucination incidents** — owner-reported wrong answers. Target zero; treat each as a P0.
- **Owner retention** — do they leave it switched on for four consecutive weeks?

**Gate to R2:** ≥60% containment, zero unresolved hallucination incidents, and ≥3 of 5 partners
still running it after four weeks.

**Kill criterion:** Containment plateaus below ~40%, or hallucinations keep recurring after two
prompt-hardening cycles. The technology isn't ready for unsupervised guest contact; retreat to a
draft-reply / copilot model where the owner approves each message.

---

### R2 — The Booking Wedge *(where it becomes a business)*

**Hypothesis:** Owners will pay real money for direct bookings, and will give us their inventory
to get them.

**Scope:**
- **The hybrid inventory model** (§5) — the core technical differentiator
- Rates and availability calendar
- Concierge answers availability and quotes prices from live inventory
- Direct booking link generated in-conversation; payment via Razorpay/Stripe
- Booking confirmation, modification, cancellation policy
- Attribution: every booking tagged to the conversation that produced it

**Metric:** **Direct bookings attributed per property per month**, and the commission that
represents. This is the number that appears on the invoice.

**Pricing moves here** — and the pricing model is a product decision, not a finance one:
- Below ~₹2,000/mo nothing is worth the support burden.
- Straight SaaS pricing caps upside and forces you to argue about features.
- **Recommended: low base + a share of recovered direct-booking value.** The pitch is "you pay us
  out of what you were already paying Booking.com," which makes the sale arithmetic instead of
  persuasion. It also aligns the roadmap: we only grow when they do.

**Gate to R3:** at least one property where attributed direct bookings exceed our fee by 3×. If
we can't prove that at one property, we cannot sell it at a hundred.

**Kill criterion:** Guests take the quote and book on the OTA anyway. That means the OTA's trust
and cancellation terms outweigh price — a much harder problem, and a signal to sell into the
*ops* side rather than the demand side.

---

### R3 — The PMS Core

**Hypothesis:** With bookings flowing through us, owners will move daily operations here too.

**Scope:**
- Bed-level assignment calendar with drag-and-drop
- Check-in / check-out, guest profiles, stay history
- **ID capture and compliance** — see §6, this is a wedge in its own right
- Housekeeping: room/bed status, turnover queue
- Multi-property for small chains
- Basic reporting: occupancy, ADR, RevPAB, channel mix

**Metric:** Daily active use by front-desk staff — not owners. Owners buy; staff decide whether
it survives. If the front desk keeps a parallel WhatsApp group or notebook, we've lost.

**Kill criterion:** Staff work around it. Go watch a shift in person before writing another line.

---

### R4 — Distribution & Scale

**Hypothesis:** We can be the single source of truth for inventory across all channels.

**Scope:** Channel manager (Hostelworld, Booking.com, Airbnb), rate parity, overbooking
protection, self-serve onboarding, in-product billing, public API.

This is the real moat and the hardest engineering in the roadmap. **Do not start it before R3 is
retained.** Channel management is a support and correctness nightmare — every sync bug is a
double-booked guest standing in a lobby — and it's only worth that pain once properties genuinely
live in the product.

---

## 4. Data architecture: decide once, in R1

### 4.1 Multi-tenancy

The single most expensive thing to retrofit. Decide before writing R1's first migration.

**Recommendation: shared schema, `tenant_id` on every row, enforced by Postgres Row-Level
Security** — not by application code remembering to add a `WHERE` clause.

- Application-level filtering is one forgotten predicate away from showing Hostel A's guest list
  to Hostel B. That's not a bug, it's an incident, a DPDP notification, and the end of the
  design-partner relationship.
- RLS enforces isolation in the database, so a mistake in a service or a rogue ad-hoc query still
  can't cross tenants.
- Schema-per-tenant looks safer and becomes unmanageable at ~100 tenants (migrations × tenants).
  Database-per-tenant is right for enterprise ACVs, which is not this market.

Tenant context is resolved once per request from the authenticated principal, set as a Postgres
session variable, and never accepted from a request body or header. **The tenant id must never be
a client-supplied value** — that's a horizontal privilege escalation waiting to happen.

### 4.2 How many datastores

**Redis, from R1 — unambiguous yes.** It earns its place immediately and on four separate
counts: rate limiting the WhatsApp webhook and the chat endpoint (which today has neither, see
[phase-1-review.md](phase-1-review.md) finding #9), per-tenant LLM token budgets, caching
availability lookups in R2 (the hottest read in the system, and the one the concierge hits on
every "do you have space?"), and a distributed lock for booking contention. Any one of these
would justify it.

**A second primary datastore for chat transcripts and knowledge bases — not yet, and the
reasoning matters more than the verdict.**

The case for it is real: transcripts are schemaless, write-heavy, rarely read, and grow without
bound — genuinely different access patterns from relational business data. Under enough volume
that separation is correct.

The case against it, at this stage, is stronger:

- **Attribution stops being transactional.** R2 requires every booking tagged to the conversation
  that produced it. Split across two databases, "write the booking and mark the conversation as
  converted" is no longer one atomic unit — you inherit dual-write consistency, and you inherit
  it in the revenue path, which is the worst possible place.
- **DPDP gives you two deletion paths.** A guest erasure request must purge PII from every store.
  Two stores means two purge routines that must both be correct and stay correct. That is a
  compliance gap that grows quietly.
- **Postgres JSONB already covers the actual requirement.** Schemaless documents, indexable with
  GIN, queryable, in the same transaction, with one backup and restore story.
- **Operational cost is paid by one person.** Two databases means two backup regimes, two restore
  drills, two upgrade paths, two monitoring surfaces.

There is also an interview dimension here worth being blunt about, since it cuts the opposite way
to intuition. Senior interviews probe *judgment*, not stack breadth, and adding a document store
to a five-tenant system is a recognizable resume-driven-architecture smell. "I used Mongo for the
unstructured data" invites the follow-up *"what did that buy you over JSONB?"* — and there is no
strong answer at this scale. Whereas **"I evaluated a document store and chose JSONB, because
booking attribution has to be transactional and I didn't want two erasure paths under DPDP"** is
a genuinely senior answer that demonstrates you know the trade-off *and* know when not to take it.

**Decision: Postgres (relational + JSONB) and Redis from R1. Revisit a dedicated transcript store
when a concrete trigger fires** — transcript volume passing roughly 50–100M rows, or transcript
writes measurably contending with booking transactions. Write the trigger down now so the
decision is evidence-driven later rather than taste-driven. When it does fire, evaluate Postgres
partitioning and cold-storage archival alongside a document store; at that point the winner is
not obvious, which is exactly why it shouldn't be decided today.

---

## 5. The hybrid inventory model — the real differentiator

Worth thinking hard about, because it's what nobody else models properly and it's the thing an
interviewer will happily spend twenty minutes on.

The trap: model a hostel as "rooms with beds." It falls apart on the first real case.

**The crux:** in a hostel, one physical room can be sold **two mutually exclusive ways** — as
six dorm beds, or as one private room. Sell a single bed and the whole-room product must
instantly disappear from every channel. Sell it as a private and all six beds must vanish. These
are not two inventories; they are two *views* of one capacity, and they must never both be
bookable at the same time.

Sketch:

```
Property
└── Space              a physical room, capacity N
     ├── sale modes:   WHOLE | PER_UNIT   (which are enabled)
     └── Unit[]        the individual beds (only for PER_UNIT)

SellableProduct        what a guest actually buys
     ├── DORM_BED      → allocates 1 Unit for a date range
     ├── PRIVATE_ROOM  → allocates the whole Space for a date range
     └── EXPERIENCE    → a dated bundle: unit-nights + non-inventory items
```

Two properties any implementation must guarantee:

1. **Allocating a Unit blocks whole-Space sale for those dates, and vice versa.** Enforce it in
   the database with an exclusion constraint over a date range, not in application logic. Two
   concurrent bookings on the last bed is the classic PMS failure, and it happens under exactly
   the load you get when a channel manager syncs.
2. **A retreat is a bundle, not a room type.** "7-day yoga retreat, 12 spots" consumes 12 ×
   7 bed-nights plus meals and instruction that have their own capacity. Modeling it as a room
   type is the hack every incumbent makes, and it's why owners run retreats in a spreadsheet.

### 5.1 The availability read path — where the algorithms live

The database constraint above and an in-application interval algorithm are **two different layers
solving two different problems.** They are not alternatives, and choosing one *instead of* the
other is the mistake to avoid.

**The constraint is the correctness boundary, and nothing in application code can replace it.**
An in-memory interval tree cannot make a booking safe: two requests hitting two application
instances both consult their own structure, both observe the last bed as free, and both write.
The tree was correct in each process and the guest is still standing in a lobby with a
confirmation for a bed that doesn't exist. Only the database — a `gist` exclusion constraint over
`(unit_id, daterange)`, or serializable isolation — makes check-and-write atomic across processes.
Keep it, and keep it as the last line of defence rather than the first.

**The algorithm belongs on the read path, and there the problem is genuinely interesting.** The
question "which of these 40 beds are free on which of the next 60 days, in both sale modes" is
asked constantly — by the concierge on every availability question, by the calendar UI on every
render, by the channel manager on every sync — and the naive approach is O(units × days ×
bookings) with a query per cell.

Two things make it more than a textbook exercise:

- **The sale modes are coupled.** Whole-space availability is the intersection of every child
  unit's free intervals; unit availability must additionally subtract any whole-space booking
  covering it. One set of intervals, two derived views, each constraining the other — that
  coupling is the part that isn't in the textbook.
- **The output is a merged interval set, not a boolean.** A sweep line over booking start/end
  events, ordered by date, maintaining a running count of occupied units per space, produces the
  entire calendar in roughly O((B + U) log B) plus output size — a different complexity class
  from the naive scan, on a query that runs on every keystroke in the calendar UI.

That is a real application of interval/sweep-line work to a real product surface, and it survives
follow-up questions in a way a contrived example doesn't. The framing that lands in an interview
is **"exclusion constraint for correctness, sweep-line for the read path, and here is why neither
one can do the other's job."** The framing that fails is "I implemented an interval tree instead
of using a database constraint" — the very first follow-up will be about two concurrent bookings,
and there is no good answer to it.

Two testing notes, because they're what turn this from a claim into evidence:

- **Write a concurrency test**, not just a unit test: N threads racing for the last bed, asserting
  exactly one succeeds and the rest fail cleanly. That single test is more persuasive than any
  amount of describing the design.
- **Property-based testing fits this unusually well** — generate random booking sets, assert the
  sweep-line result always matches a naive brute-force oracle. It finds the off-by-one at the
  interval boundaries that hand-written cases miss, and boundary handling (is checkout day
  bookable? yes, and half of all PMS bugs live there) is exactly where this goes wrong.

Getting this right in R2 is what makes R3 and R4 possible. Getting it wrong means a rewrite at
exactly the moment you have paying customers.

---

## 6. Compliance as a wedge, not a chore

Two obligations, both real, both currently painful:

**Form C (Bureau of Immigration, India).** Every property hosting a foreign national must file
guest details within 24 hours. Today: manual re-typing into a government portal, universally
hated, frequently late. No international PMS automates it well because it's an India-specific
workflow with no global analogue.

This is a genuinely strong wedge — arguably stronger than the concierge for properties with
foreign guests. It is *mandatory* (not a nice-to-have), it *forces* the guest-identity data
relationship (making us the system of record by necessity), and it is defensible precisely
because global players won't prioritise one country's paperwork. Consider pulling a lightweight
version forward into R1 if design partners host foreign guests — it buys onboarding goodwill for
a fraction of the concierge's build cost.

### 6.1 Don't build a data-entry form — extract from the passport

The obvious implementation is a form the owner types passport details into. That's the version
that gets abandoned, because it is the same re-typing they already resent, wearing our logo.

Build the camera version instead: the owner photographs the passport, the backend extracts the
data, the owner confirms. Same multimodal Gemini model already in the stack — no new provider, no
new dependency, and it turns the least glamorous feature in the roadmap into the most impressive
one to watch.

**Read the MRZ, not the visual page.** The Machine Readable Zone is the two fixed-width lines at
the bottom of a passport (TD3: 2 × 44 characters, ICAO Doc 9303). It exists precisely to be read
by machines: fixed offsets, a restricted character set, no language variation. The printed visual
zone is free-form, multilingual, and inconsistently laid out.

**Then verify it, which is the part that makes this a real engineering story.** The MRZ carries
check digits — over the document number, date of birth, expiry date, and a composite over the
whole line — computed with a 7-3-1 positional weighting. That means **the model's output is
deterministically verifiable.** Recompute the checksums in Java; if they don't reconcile, the
extraction is wrong and you know it without a human looking.

This inverts the usual problem with LLM output. Ask the model only for the two raw MRZ lines —
pure OCR, which is what it is genuinely good at — and do the field parsing and checksum
validation yourself in deterministic Java. The model is never trusted to produce structured
truth; it produces characters, and arithmetic decides whether to believe them.

That division of labour is the answer to *"how do you handle hallucination in production?"* — and
"I don't trust the model, I verify its output against ICAO check digits and reject on mismatch"
is a substantially better answer than anything about prompt engineering. It's the strongest
single technical narrative available in this entire roadmap.

Non-negotiables around it:

- **Human confirmation before filing.** Never auto-submit a government form from OCR output. The
  owner reviews the parsed fields and confirms. Legal liability for a wrong filing sits with the
  property, and the UI must reflect that.
- **A manual-entry fallback that is always available.** Worn passports, glare, damaged MRZ,
  non-TD3 documents. Checksum failure must route to typing, not to a dead end.
- **Passport images are the most sensitive data in the system.** Never logged, never in an error
  payload, encrypted at rest under a separate key, deleted on a retention timer once the filing
  is accepted. Confirm the provider's data-retention and training settings *before* the first
  real passport is sent, not after.
- **Use the current multimodal model** (`gemini-2.5-flash`, escalating to `gemini-2.5-pro` on
  checksum failure before giving up), not Gemini 1.5 Pro — that generation is legacy, as already
  flagged in [phase-1-review.md](phase-1-review.md).
- **`getUserMedia` requires a secure context.** `localhost` is exempt; a staging box on plain HTTP
  is not. This will bite between local dev and first deploy — plan TLS for staging.

**DPDP Act 2023.** We will hold passport scans, guest contact details, and conversation
transcripts. Non-negotiable from R1: consent capture, purpose limitation, retention windows with
actual deletion, breach notification readiness, and a data-processing agreement with each
property (they are the fiduciary, we are the processor). Encrypt ID documents at rest with a
separate key. **Never put guest PII in an LLM prompt** — the discipline established in Phase 1's
logging rules extends here.

---

## 7. Cross-cutting tracks (run from R1, never "later")

| Track | R1 | R2 | R3+ |
| --- | --- | --- | --- |
| **Security** | Auth, RLS, secrets in a vault, dependency scanning | Payment data never touches our servers (tokenize) | Pen test, SOC2 groundwork if going upmarket |
| **AI cost** | Per-tenant token budgets, prompt caching | Cache availability answers; smaller model for classification | Route by query complexity |
| **AI safety** | Golden-question eval suite run on every prompt change | Regression tests on booking-related answers | Human review queue for low-confidence turns |
| **Reliability** | Uptime alerting, error tracking | Availability/booking SLO, idempotent booking writes | Multi-AZ, tested restores |
| **Onboarding** | White-glove, by hand | Guided setup ≤ 30 min | Fully self-serve — the PLG gate |
| **Support** | Founder's WhatsApp | Shared inbox, docs | Tiered, with SLAs |

The AI eval suite deserves emphasis: **the moment prompt changes are shipped without a regression
suite, quality becomes a coin flip.** Build the golden-question set during Phase 3's beta
sessions — those are real questions from real owners, which is exactly what a good eval set is —
and run it in CI from R1 onward.

---

## 8. Deliberately not building

Saying no is most of the job. Each of these will be asked for; each is a trap at this stage.

- **A mobile app.** WhatsApp is the guest app. A responsive web console is the staff app.
- **A revenue-management / dynamic-pricing engine.** Sounds impressive, needs data density we
  won't have for years, and owners won't trust it.
- **Full accounting.** Integrate with what they already use. Accounting is a product, not a
  feature.
- **Voice.** Ask again after R3.
- **Going upmarket to hotels.** The hybrid inventory model is the differentiator; hotels don't
  need it, and chasing them means competing with Cloudbeds on their own turf with a tenth of the
  team.
- **RAG before it's needed.** A hostel's rules are a few thousand tokens. They fit in context.
  Vector search buys nothing until knowledge bases get large or span many documents — likely R3.
  *Build it when a retrieval failure is the actual bottleneck, not before.*
- **A second primary datastore.** Postgres (relational + JSONB) and Redis cover every requirement
  through R3. See §4.2 for the trigger that would change that — and for why "I chose not to" is
  the stronger answer here.

---

## 9. The scoreboard

Five numbers. If a proposed feature doesn't move one of them, it's not on the roadmap.

1. **Containment rate** — % of guest messages handled without a human *(product quality)*
2. **Attributed direct bookings per property per month** *(the value we create)*
3. **Time to first value** — signup → first correctly answered guest question *(activation)*
4. **Net revenue retention** *(are we climbing the ladder or churning)*
5. **Gross margin per tenant** — after LLM and WhatsApp costs *(is this a viable business)*

That last one is easy to ignore and fatal to ignore. Per-message LLM and WhatsApp Business
charges scale with usage while subscription revenue doesn't. A property with chatty guests can be
served at a loss without anyone noticing for months. Instrument margin per tenant from R1 — the
Phase 1 decision to return `usage` and `latencyMs` on every response is the first brick of that,
which is why it was worth building before anything consumed it.

---

## 10. Biggest risks, honestly

| Risk | Why it's real | What reduces it |
| --- | --- | --- |
| Concierge is a feature, not a company | It genuinely is | Climb to rung 3 fast; the booking path is the business |
| Trust ceiling on AI-to-guest contact | Owners are protective of guest relationships; one bad answer costs a review | Escalate early and visibly; copilot mode as the fallback |
| Low ACV can't fund the support this needs | PMS support is heavy; hostels are price-sensitive | Self-serve onboarding is existential, not polish; usage-based pricing for upside |
| Incumbents ship a WhatsApp bot | They have distribution and will | The hybrid inventory model is the defensible part, not the bot |
| Solo-founder bandwidth | R3–R4 is a team's worth of work | Stay ruthless about §8; R0–R2 is genuinely solo-shippable |
| Resume-driven architecture | The portfolio goal pulls genuinely hard toward adding technologies, and every addition also adds operational surface carried by one person | Each one needs a written justification that survives a skeptical *"what did that buy you?"* — §4.2 is the template. Depth in three technologies interviews better than breadth across seven |

---

## 11. What to do next

1. Clear the P0/P1 items in [phase-1-review.md](phase-1-review.md).
2. Build [phase-2-frontend.md](phase-2-frontend.md).
3. Run Phase 3 with both beta hostel owners — **and capture every question they ask verbatim.**
   That transcript is simultaneously your prompt-tuning input, your R1 eval suite, and your
   evidence for the R0 gate. It is the highest-value artifact of the entire prototype, worth more
   than the code.
4. Only then decide whether R1 is worth starting.
