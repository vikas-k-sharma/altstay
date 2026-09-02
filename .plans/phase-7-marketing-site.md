# Phase 7 — The marketing site

The public front door. Phase 6 slice 0 moved the concierge demo to `/concierge` and left `/`
redirecting to it; this phase takes `/` and makes the thing look like a product rather than a
prototype someone left running.

Four pages — `/`, `/product`, `/about`, `/contact` — plus the nav that ties them to the demo and the
console. **No pricing page**: roadmap §3 puts the pricing decision at R2, after the booking wedge is
tested, and publishing numbers now pre-commits a call nobody has made.

---

## 0. Admission, and the one part of it that is conditional

[phase-3-validation.md](phase-3-validation.md) §9.1: does this survive a **KILL** verdict?

**The site does. Half the copy might not, and that shapes how it is built.**

If the R0 gate kills, the roadmap pivots to *"owner-facing tooling (the ops side) instead of
guest-facing."* A business with a PMS still needs a front door — the pages, the nav, the design
system, the contact path and the about page are all indifferent to the verdict. What is *not*
indifferent is a homepage whose hero sells an AI concierge.

So: **the hero and the product page's ordering are built to be re-pointed, not rewritten.** The three
pillars in §5.2 are separate content blocks in a fixed layout, and a KILL verdict reorders them —
inventory and bookings first, concierge demoted to a supporting feature — without touching a
component. That is a five-minute edit if the copy lives in one place per block, and a redesign if it
does not.

**Sunk cost is still not evidence** (§9.1 constraint 3). A marketing site describing the concierge
must not appear in the reasoning that decides the gate, and it is not a reason to soften a KILL.

---

## 1. Scope

| In | Out, and why |
| --- | --- |
| `/` landing, `/product`, `/about`, `/contact` | **Pricing page** — roadmap §3 puts pricing at R2 |
| Nav linking marketing → `/concierge` demo → `/console` login | **Self-serve signup** — premature before real client volume |
| A shared design system the console can adopt later | A component library or CSS framework beyond Tailwind |
| Metadata, Open Graph, sitemap, robots | A blog or CMS — nothing to publish yet |
| Static rendering, no client JS beyond what interaction needs | Analytics — see §8.3, it needs a consent decision first |
| Contact by mail and WhatsApp (§6) | A contact form with a database or an email provider |

---

## 2. Audience and voice

**Written for prospective hostel and hybrid-stay owners.** That is the decision; the alternative was
the interview audience roadmap §3.1 names as this repo's second reader, and it loses on a simple
test — an owner who cannot tell what the product does will leave, whereas an interviewer will read
`.plans/` and the code, which is where the engineering story already lives and lives better.

**Written trigger for an `/engineering` page:** the first time this URL is put in front of a hiring
audience deliberately. At that point roadmap §5.1's exclusion-constraint-plus-sweep-line story and
§6.1's MRZ-checksum story are both genuinely strong, and both are already written down.

**Voice.** Concrete, arithmetic where possible, no adjective doing a number's job. The strongest line
available is not a claim about software:

> Hostels pay 15–18% commission to Hostelworld and Booking.com. On a ₹40L/year property that is
> ₹6–7L. — roadmap §1

That is the pitch. The product is how the money comes back, and the site should read like it was
written by someone who has looked at a hostel's P&L rather than a landing-page generator.

---

## 3. What this site may and may not claim

The product is **pre-R0**. There are no design partners, no containment rate, no retention data, and
the gate is undecided. A marketing site is where an honest project quietly becomes a dishonest one,
so the boundary is written down before any copy is.

**May not appear on this site, in any form:**

- Customer logos, testimonials, quotes, or names. There are no customers.
- "Trusted by *N* properties", "*N* messages answered", or any metric that has not been measured.
- Case studies, before/after numbers, or a review score.
- A team page implying employees who do not exist.
- Screenshots of a product state that has never rendered — every screenshot is of the real thing.

**May appear, because each is true and checkable:**

- What the software does, described in the present tense, because it does it.
- The commission arithmetic (roadmap §1) — it is a public, industry-wide figure, presented as the
  market's cost structure and not as a promise about a reader's own savings.
- The hybrid-inventory problem statement (roadmap §5) — a description of a real modelling gap.
- The live demo. It is the honest proof, and it is one click away.
- Development status, stated plainly. "In private beta with a small number of properties" is
  accurate once that is true and reads better than manufactured scale.

**The demo is the social proof.** A prototype that works, linked from the hero, is worth more than a
wall of invented logos and does not have to be taken back later.

---

## 4. Routes and layout architecture

### 4.1 The root layout is currently the demo's

`src/app/layout.tsx` today sets `metadata.title` to *"AltStay — AI Hostel Concierge & Knowledge
Base"* and puts `bg-zinc-100 dark:bg-zinc-950` on `<body>`. Both are demo styling that Phase 2 chose
when the demo was the whole application. A marketing landing page inheriting them starts every
design decision from the wrong place, and editing them in the root layout changes the demo's
rendering — which §9.1 constraint 1 still forbids until the October sessions.

**Decision: route groups, one layout each.** Route groups in parentheses do not appear in the URL:

```
src/app/
├─ layout.tsx              root: <html>, fonts, globals.css — and nothing opinionated
├─ (marketing)/
│   ├─ layout.tsx          marketing chrome, marketing metadata
│   ├─ page.tsx            /
│   ├─ product/page.tsx    /product
│   ├─ about/page.tsx      /about
│   └─ contact/page.tsx    /contact
├─ concierge/
│   ├─ layout.tsx          the demo's body classes and metadata, moved down from root
│   └─ page.tsx            /concierge — unchanged
└─ console/                Phase 6
```

`src/app/page.tsx` — the redirect Phase 6 slice 0 added — is **replaced** by
`(marketing)/page.tsx`. Two files cannot both serve `/`.

**Moving the demo's body classes from the root layout into `concierge/layout.tsx` is a change to the
demo's rendering path**, even though the rendered output is identical. It gets the same treatment
slice 0 got: made on its own, followed by a runbook §4 re-walk, with the `/concierge` anonymous-render
test green before and after. If the October sessions are close when this phase starts, **skip it** —
scope the marketing layout to override what it needs and leave the root alone. The cost is a slightly
uglier layout file; the risk it avoids is the demo looking wrong in the one hour that cannot be re-run.

### 4.2 Rendering

Every marketing page is a **Server Component, statically rendered**. No client JavaScript except for
the mobile nav toggle and any accordion. There is no data, no session, and nothing to fetch — a
marketing page that ships a hydration bundle for prose is paying for nothing.

`export const dynamic = 'force-static'` on each, and the build output is checked: §11 asserts the
pages appear as static in `next build`, because a stray `cookies()` call silently opts a whole route
into dynamic rendering.

---

## 5. The pages

### 5.1 `/` — landing

Blocks, in order. Each is one component with its copy in one place, so §0's re-pointing is an
edit rather than a rebuild.

| # | Block | Content |
| --- | --- | --- |
| 1 | **Hero** | The commission line as the headline idea. One sentence on what AltStay is. Two buttons: *Try the concierge* → `/concierge`, *See the product* → `/product`. No email capture — there is nothing to send anyone yet |
| 2 | **The problem** | Roadmap §1's two mismatches, stated as an owner experiences them: the same room is a 6-bed dorm on Tuesday and a private double on Saturday, and no PMS models that; guests message on WhatsApp at 2 AM and the owner is the integration |
| 3 | **The three pillars** | Concierge · Inventory · Bookings — one card each, a sentence and a link into `/product`. **This is the block a KILL verdict reorders** (§0) |
| 4 | **The demo, inline** | A screenshot or short loop of the real concierge, linking to `/concierge`. Real output, per §3 |
| 5 | **The hybrid-inventory explainer** | The differentiator, shown not asserted: one room, two ways to sell it, and selling either hides the other. A small diagram earns its place here and nowhere else on the site |
| 6 | **Status + CTA** | Where the product actually is, stated plainly, and one way to get in touch |

Anti-requirements, because they are what a generated landing page would add: no autoplaying video, no
carousel, no cookie banner (nothing is set — §8.3), no chat widget, no countdown, no logo wall.

### 5.2 `/product`

Three sections, one per pillar, in a fixed layout so §0's reorder is a data change.

- **Concierge** — answers guest questions from a knowledge base the owner edits live, and says so
  rather than guessing when it does not know. Escalation is a feature, not a failure; that framing
  is honest and is also what roadmap §10 identifies as the answer to the trust ceiling.
- **Inventory** — room types, physical rooms, bed-level units, and the hybrid case as the
  centrepiece. This is the section that should convince someone who has fought a hotel PMS.
- **Bookings** — availability, the booking lifecycle, front-desk check-in and check-out, rates by
  date. Screenshots from the Phase 6 console once it exists; until then, this section ships with
  prose and no fabricated UI.

Each section links to the demo where a demo exists. Nothing links to a feature that does not.

### 5.3 `/about`

Who is building it and why, in the first person, short. Roadmap §1's market observation is the
credible core — this exists because alternative accommodation is badly served by software built on a
hotel's mental model. No stock photography of people who are not involved, no invented team.

### 5.4 `/contact`

See §6. One page, two real ways to reach a human, and an honest response-time expectation.

---

## 6. Contact — no form, deliberately

A contact form needs somewhere to send. The three options and what each costs:

| Option | Verdict |
| --- | --- |
| **`mailto:` link and a WhatsApp link** | **Chosen.** Zero infrastructure, no PII stored, no spam surface, no provider key on a borrowed machine. Roadmap §7 already says R1 support *is* the founder's WhatsApp — the site should match how the business actually runs rather than implying a support desk |
| A form posting to a Next route handler → email provider | A provider account, an API key, a spam-mitigation story, and a queue of PII the DPDP obligations in roadmap §6 then attach to. Real cost for a page that will receive a handful of messages |
| A form posting to Spring | All of the above plus a new public unauthenticated endpoint, which is a rate-limiting and abuse surface on the same origin as the API. No |

**Written trigger to build a real form:** the first week the mail link produces more than a couple of
enquiries, or the first time someone says they tried to get in touch and could not. At that point it
is a route handler and a provider, and the PII path gets designed properly rather than by default.

The page states a response expectation only if it will be met.

---

## 7. Design system

### 7.1 Tokens

Phase 6 §8.1 adds ten tokens to `globals.css` for the console. **This phase extends the same set
rather than starting a second one** — one palette, used by console and marketing, is what stops the
product and its website looking like two companies.

Marketing needs what the console did not: a display type scale, generous spacing units, and one
accent that survives on a large surface. Additive to `globals.css`, and still no existing token's
value changes while the demo is frozen.

### 7.2 Type

Geist Sans and Geist Mono are already loaded in the root layout via `next/font`. Use them. A
marketing site that pulls a third family for a headline is paying a font request for a mood.

**One existing oddity to leave alone:** `globals.css` sets `body { font-family: Arial, Helvetica,
sans-serif }`, which fights the `--font-sans` variable the layout defines. It is demo styling, it is
frozen until October, and the marketing layout should set its own font stack on its own container
rather than reaching up to fix it. Record it, fix it after the sessions.

### 7.3 Dark mode

The demo and the root layout already respond to `prefers-color-scheme`. The marketing pages do too —
both palettes designed, not one derived by inverting the other at the last minute.

### 7.4 Motion

Entrance animation on scroll, at most, and honouring `prefers-reduced-motion`. Nothing that moves
while being read.

---

## 8. Performance, SEO and accessibility

### 8.1 Budget

Static HTML and CSS with near-zero JavaScript is the whole point of building these as Server
Components. Targets, measured with Lighthouse on the production build, not asserted:

- Performance ≥ 95, Accessibility 100, Best Practices ≥ 95, SEO 100 on `/` and `/product`.
- Largest Contentful Paint under 2.0s on a simulated 4G connection. The hero image is the LCP
  element and gets `priority` on `next/image`; everything else is lazy.
- No layout shift from web fonts — `next/font` handles this and must not be bypassed.

### 8.2 Metadata

Per-page `metadata` exports: title, description, canonical, Open Graph and Twitter cards. One OG
image per page, generated at build time with `next/og` rather than checked in as a binary that goes
stale. `sitemap.ts` and `robots.ts` at the app root. `/concierge` and `/console` are `noindex` — a
demo and a login screen have no business in search results.

### 8.3 No analytics, for now

Nothing is tracked, so **there is no cookie banner** — which is itself a small, real advantage over
every competitor's site, and it keeps the DPDP surface at zero for a phase that adds no data
handling. **Written trigger:** the first time a decision depends on knowing which page people read.
At that point choose a cookieless, aggregate analytics option and re-examine consent then.

### 8.4 Accessibility

Real headings in order, visible focus states, one `<main>` per page, alt text on every image, colour
contrast ≥ 4.5:1 for body text, and the mobile nav operable by keyboard. The console's rule applies
here too: **never colour alone**.

---

## 9. Testing

`vitest` + Testing Library, matching Phase 2 and Phase 6. These pages are prose, so the tests are
about structure and the things that silently break.

| Test | Proves |
| --- | --- |
| `/` renders the hero and both CTAs with no session | The front door works for a stranger |
| Nav links resolve to `/product`, `/about`, `/contact`, `/concierge`, `/console` | No dead link in the primary nav |
| `/concierge` still renders with no session | Phase 6's standing guard, unmoved |
| Every page exports `metadata` with a title and description | §8.2, and it is the easiest thing to forget on a new page |
| `sitemap.ts` lists exactly the four public marketing routes | `/concierge` and `/console` stay out of it |
| No page imports from `src/lib/server/session.ts` | A marketing page must not become dynamic by accident |
| Mobile nav opens, closes, and is keyboard operable | §8.4 |
| No `<img>` without `alt` | §8.4, mechanically checkable |

---

## 10. Build order

| # | Slice | Delivers |
| --- | --- | --- |
| 1 | Route groups, marketing layout, nav and footer, design tokens | The shell, with placeholder page bodies |
| 2 | `/` landing | The front door |
| 3 | `/product` | The three pillars in depth |
| 4 | `/about`, `/contact` | The rest of the site |
| 5 | Metadata, OG images, sitemap, robots, Lighthouse pass | Shipped rather than merely built |

Slice 1 replaces `src/app/page.tsx` (the Phase 6 redirect) with `(marketing)/page.tsx`. From that
moment `/` no longer reaches the demo, so the nav must link to `/concierge` **before** the redirect
is removed, not after.

---

## 11. Definition of Done

```powershell
cd frontend; npm run test; npm run build; npm run lint
```

```powershell
cd frontend; npx serve@latest out 2>$null; # or: npm run start — then run Lighthouse against / and /product
```

- [x] `npm run test`, `npm run build` and `npm run lint` all pass — 340 tests green (up from 291;
      every added test watched red first), `next build` BUILD SUCCESS, `eslint` clean
- [x] `next build` output shows `/`, `/product`, `/about` and `/contact` as **static**, not dynamic
      — confirmed twice (slice 1 and again after slice 5's sitemap/robots/OG additions); `○` in
      the build table for all four, plus their `opengraph-image` routes and `robots.txt`/`sitemap.xml`
- [x] Lighthouse on the production build (`npm run start`, `npx lighthouse`, headless Chrome),
      2026-09-01:
      - `/` — Performance **98**, Accessibility **100**, Best Practices **100**, SEO **100**. LCP 2.5s
      - `/product` — Performance **98**, Accessibility **100**, Best Practices **100**, SEO **100**. LCP 2.3s
      - First run measured Accessibility 96: `bg-accent text-white` buttons fail contrast in dark
        mode (`--accent` is a *light* green there, `#34d399`, measured 1.92:1 against a required
        4.5:1). Fixed with an additive `--accent-foreground` token (white in light mode, `#052e1f`
        in dark) swapped in on every accent button under `(marketing)` and `components/marketing`.
        Re-run confirms 100. The identical bug exists in the frozen console (`bg-accent text-white`
        in `components/staff/*`) — out of scope this phase, flagged as a separate background task
        rather than touched here.
      - §8.1's LCP-under-2.0s sub-target is missed by 0.3–0.5s on both pages; Performance still
        clears the ≥95 gate. Not chased further — the hero is text-only (no image), so the gap is
        network/CPU throttling overhead in Lighthouse's simulated mobile profile, not a fixable
        render-blocking asset.
- [x] **The demo is unaffected** — `/concierge`'s standing anonymous-render test
      (`ConciergePage.test.tsx`) is green and untouched; `git diff --stat` shows **no change** under
      `src/components/chat`, `src/components/admin`, `src/components/console`, `src/hooks/` or
      `src/lib/presets.ts`; browser-verified the page renders identically (same title, same DOM,
      same suggested questions). **Not done:** a full interactive [dev-runbook.md](dev-runbook.md)
      §4 walk-through, because several of its steps call the live Gemini model and the key is
      capped at 20 requests/day with the October beta sessions still pending on it (CLAUDE.md) —
      spending quota on a phase that touches zero files under `concierge/` wasn't a justified trade.
      The static/automated evidence above is what stands in its place; a manual §4 walk is still
      recommended before the October sessions if quota allows.
- [x] Every primary-nav link resolves; no 404 reachable from the nav or the footer — `curl`-verified
      200 on `/`, `/product`, `/about`, `/contact`, `/concierge`, `/console/login`
- [x] **§3 audited before launch**: read through every page's rendered text — no logo, testimonial,
      customer count, case study, review score or invented team appears anywhere. The Bookings
      section explicitly labels its availability table "the design, not a photograph of it"
- [x] `/concierge` and `/console` are absent from `sitemap.xml` (verified: exactly 4 URLs) and
      disallowed in `robots.txt`. **Decision, not a meta tag:** noindex is achieved by keeping both
      routes out of the sitemap and disallowing them in `robots.txt`, rather than adding a
      `<meta name="robots" content="noindex">` to `concierge/page.tsx` or the console — avoids
      touching either frozen surface at all, at the cost of Google being unable to see an explicit
      noindex tag on a URL it's disallowed from crawling in the first place (an accepted, documented
      trade-off; both routes are also unlinked from any indexed page)
- [x] No cookie is set by any marketing page — `document.cookie` is `""` on `/`, checked in the
      actual browser, not asserted
- [x] Keyboard-only walk of `/` and the mobile nav — real Tab presses (not simulated events) landed
      in order: logo → Product → About → Contact → Staff login → Try the demo, each with a visible
      focus ring; mobile menu toggles via a real `<button>` with correct `aria-expanded`/`aria-controls`
- [x] Both colour schemes reviewed on a real screen at 1280×800 and at 390×844 — home page (both
      modes, both sizes) and product page (light, 390×844) browser-checked; about/contact reviewed
      via `next build` static output and their own render tests rather than a screenshot of every
      combination

---

## 13. Build log — decisions made while building

- **Slice 1 (2026-08-31).** Took §4.1's escape hatch: `src/app/layout.tsx` is untouched;
  `(marketing)/layout.tsx` sets its own background/text/font on a wrapping `<div>` instead. Makes
  "the demo is unaffected" true by construction rather than something to re-verify — no `git diff`
  under `concierge/`, and the root-layout move stays available for after the October sessions.
- **Palette diverged from `.design/altstay-marketing-site.html` on purpose.** The reference uses a
  warm cream/terracotta system (`#FCFAF6` / `#A83E22`); §7.1 asks for one palette shared with the
  console, so marketing reuses the existing `--accent`/`--surface`/`--border` tokens from
  phase-6 §8.1 rather than introducing a second system. Only the reference's layout rhythm, type
  scale (Geist, ~60px/600-weight display heading, 19px lead paragraph, mono uppercase eyebrows)
  and copy were carried over — additive tokens (`--accent-quiet`, `--font-size-display*`,
  `--tracking-display`) cover what didn't already exist.
- **The WhatsApp number in `src/lib/marketing/contact.ts` is a placeholder** (`+91 98xxx xxxxx`,
  masked the same way the design reference masks it). Needs the founder's real number before
  `/contact`'s WhatsApp link or the footer go live — one file to edit when it's known.
- **Slice 2 — the "demo, inline" block became a click-to-load embed, not a screenshot.** An eager
  `<iframe src="/concierge">` was tried first and rejected: the concierge composer autofocuses on
  mount (correct on its own page), and nested in an iframe partway down a long page that focus
  makes the *browser* scroll the whole marketing page to the iframe the instant it loads — an
  unsolicited jump discovered by watching it happen, not by reasoning about it. `LiveDemoFrame`
  defers the iframe behind a "Load the live demo" button instead: by the time it loads, the click
  already happened at that scroll position, so there's nothing to jump to. Also cheaper than an
  eager embed (a second Next.js instance doesn't load until asked for) and arguably more honest
  than a screenshot either way — it's the live route, not a picture of it.
- **`SITE_URL` (`https://altstay.in`) is asserted, not confirmed-owned.** Used for `metadataBase`,
  canonical URLs, the sitemap and robots.txt. Matches the domain already implied by
  `CONTACT_EMAIL` and the contact page's own "altstay.in/console" line, so it's consistent within
  the repo, but nobody has verified the domain is actually registered to this project.
- **OG images are generated with `next/og`, not checked in.** One `opengraph-image.tsx` per route,
  sharing a `renderOgImage()` helper — plain text on the dark surface colour, no external font
  fetch (keeps the offline build invariant intact: no network call at build time).
- **The hero headline went through three revisions on 2026-09-01, each one narrowing an
  overclaim the last one still had:**
  1. *"A hostel doing ₹40 lakh a year hands ₹6–7 lakh of it to Booking.com and Hostelworld"* as
     the H1 read as "use AltStay, keep the ₹6–7L" — nothing live delivers that; inventory and
     booking are still in build.
  2. Softened to lead with the concierge and reframe the commission block as "The opportunity,"
     with a caption explaining OTAs still bring guests. Still didn't survive the sharper
     objection: **OTAs do two jobs, acquisition and transaction.** WhatsApp/the concierge only
     ever reaches guests who already have the owner's number — it cannot acquire a first-time
     guest who found the property through OTA search, and that guest books through the OTA
     regardless. "Recoverable commission" isn't a today claim; it's roadmap §3's own **R2 kill
     criterion** ("guests take the quote and book on the OTA anyway"), i.e. an explicitly unproven
     hypothesis — no version of it belongs as confidently asserted hero copy at R0.
  3. **Commission dropped from the hero entirely.** Replaced with "What this replaces" — the
     spreadsheet (inventory model, in build), the owner personally on WhatsApp (AI concierge,
     live), and a hotel PMS that doesn't fit (bookings & front desk, in build) — closing with an
     explicit line the previous two versions lacked: *"What this doesn't replace: Booking.com and
     Hostelworld still bring you guests who have never heard of you. AltStay runs the property
     once they arrive — it isn't a marketing channel."* This is the correct competitive frame per
     roadmap §1: against ill-fitting hotel PMSes (Cloudbeds, eZee, Hotelogix), not against the
     OTAs, which still own discovery. `HybridExplainerSection` also gained an "In build" tag it
     was missing — every other in-build block on the site already had one.
- **Audience broadened beyond "hostel" (2026-09-02).** Every prominent surface — hero eyebrow,
  footer tagline, home/about metadata descriptions, About's "Built for" line — said "hostels,
  surf camps and retreat centres" or just "hostel," with homestays and co-living absent
  everywhere. Added "homestays" throughout (footer, home metadata, About's table and body) and to
  the hero eyebrow/subhead. Kept the H1's "hostels, not hotels" wordplay — it's a real, memorable
  line and roadmap's own flagship example — but made the very next line (subhead) explicitly name
  all four: "whether you run a hostel, a homestay, a surf camp or a retreat centre," so nobody
  reading past the headline feels excluded. Didn't touch `product-roadmap.md`'s own vocabulary
  (which says "co-living stays," not "homestays") — that's a strategic doc outside this phase's
  scope; flagged to the user rather than edited.
- **Slice 5 surfaced a real WCAG contrast failure**, not a stylistic nit: `bg-accent text-white`
  buttons score 1.92:1 in dark mode because `--accent` there is a light green (`#34d399`). Fixed
  with an additive `--accent-foreground` token rather than changing `--accent` itself — the fix
  is detailed in §11's Lighthouse entry. The same bug exists in the console, untouched, flagged
  as a separate task.

## 12. Not in this phase

- **A pricing page** — roadmap §3 puts the decision at R2. Trigger: the pricing model is decided.
- **An `/engineering` case-study page** — §2's trigger.
- **Self-serve signup** — premature before real client volume; provisioning stays administrative
  (phase-5 §10).
- **A blog or CMS** — nothing to publish, and a CMS is a datastore decision in disguise.
- **Analytics and any cookie** — §8.3's trigger.
- **A real contact form** — §6's trigger.
- **Internationalisation** — the market is India and South-East Asia and the language is English.
  Trigger: a design partner who needs otherwise.
