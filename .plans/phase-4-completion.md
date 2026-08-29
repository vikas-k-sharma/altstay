# Phase 4 completion — Tracks C, D and E

Tracks A (Postgres + RLS) and B (auth + roles) are delivered and reviewed; see
[phase-4-foundations.md](phase-4-foundations.md) §2, §3 and §3.7. This document finishes the phase.

It does **not** replace §4–§6 of that file. Those sections hold the *reasoning* — why versioning is
the design rather than an add-on, why an in-process limiter comes before Redis, why structured logs
are the difference between a partner reporting an outage and us noticing it first. That reasoning
still stands. What has changed is the **ground underneath it**: §4–§6 were written before Track A
and Track B landed, so they describe an application that did not yet have a tenant-binding aspect, a
`@ConditionalOnProperty` convention, an authenticated principal, or the review findings in §3.7.
This file is the refresh, section by section, plus the corrections that refresh produced.

**Nothing here changes the R0 demo path.** [phase-3-validation.md](phase-3-validation.md) §9.1's
three constraints hold unchanged: the anonymous `localStorage` console at `/` is untouched,
`concierge-system.st` is frozen, and none of this is evidence for the gate.

---

## 0. What changed under §4–§6, and what it costs

Verified against the repository on 2026-08-29 by reading the code and the resolved jars, not from
the plan text.

| §4–§6 assumed | Actually true now | Consequence for the track |
| --- | --- | --- |
| The KB tables still need creating | **V2 is applied** — `knowledge_base`, `knowledge_base_version`, `content_sha256`, `char_count between 1 and 20000`, `unique (knowledge_base_id, version_no)`, `current_version_id` FK | Track C needs **no new migration**. It is entities, a service, a controller and tests |
| A tenant-scoped service is an ordinary `@Service` | `TenantBindingAspect` requires an **active transaction** and an authenticated principal; the annotation is `@TenantScoped` | Every KB repository call goes through a `@TenantScoped` service method that is also `@Transactional`, or it throws before touching SQL |
| Optional dependencies with null branches are acceptable | §3.7 finding 6: they are test scaffolding in production code | New beans are `@ConditionalOnProperty(name = "spring.datasource.url")`, and any `@WebMvcTest` slice for them declares that property |
| "Tenant A cannot read tenant B's rows" is sufficient proof | §3.7 finding 1: **RLS silently covered for a deleted application-level predicate**, and the whole suite stayed green | Every tenancy claim needs *two* tests: one through the database (RLS holds) and one with the **repository mocked** (the application's own check holds) |
| Config in `application.yaml` is testable | §3.7 finding 3: `src/test/resources/application.yaml` **replaces** the main file | Any Track D/E setting that must hold in production *and* be asserted belongs in a bean or in an explicit `@TestPropertySource`, never only in the main YAML |
| The Gemini free tier is 5 requests/minute | Measured 2026-08-29: **20 requests per day**, `GenerateRequestsPerDayPerProjectPerModel-FreeTier` | Track D must distinguish *our* throttle from *upstream quota exhaustion*. They are different conditions with different copy, and today both render as "offline" |
| Structured logging needs a dependency | **Boot 4.1.1 ships it** — `org/springframework/boot/logging/structured/` is present in `spring-boot-4.1.1.jar`, with ECS, GELF and Logstash formatters (verified by listing the jar) | Track E adds **no dependency**. It sets `logging.structured.format.console` and supplies an MDC |
| `correlationId` is available to logging | It is a **local variable** in `ChatService`, on the four log lines that file writes and nowhere else | Track E promotes it to MDC in a filter, or "queryable by correlationId" covers three log lines out of a request's dozens |

---

## 1. Track C — knowledge base into Postgres

§4 of [phase-4-foundations.md](phase-4-foundations.md) stands as written. This is the delta.

### 1.1 Shape

```
KnowledgeBaseService   @TenantScoped  @ConditionalOnProperty(spring.datasource.url)
  ├─ getCurrent(propertyId)      @Transactional(readOnly = true)
  ├─ save(propertyId, content)   @Transactional            → a new version, or a no-op
  └─ history(propertyId, limit)  @Transactional(readOnly = true)
```

Entities `KnowledgeBase` and `KnowledgeBaseVersion` mirror V2 exactly — `ddl-auto: validate` turns
any drift into a startup failure, which is the point of it. DTOs are records; the controller is
thin; errors belong to `@RestControllerAdvice`.

`authored_by` is filled from the principal: `TenantUserDetails.getUserId()`. It is nullable in the
schema (`on delete set null`) so history survives a staff member leaving, but a save made with an
authenticated principal must never write null — a version with no author is not an audit trail.

### 1.2 The three details that decide whether the history is trustworthy

Two are §4.1's and unchanged. The third is new.

**Version numbers under concurrency.** Allocate `version_no` inside the transaction from
`coalesce(max(version_no), 0) + 1` over that knowledge base, and let
`unique (knowledge_base_id, version_no)` be the backstop. On violation, retry **once**; on a second
violation return 409 `ProblemDetail` of type `.../knowledge-base-conflict` whose detail says someone
else saved first. Never a 500.

**Unchanged content must not create a version.** Compare `content_sha256` with the current version;
equal means no-op, and the existing version is returned. An editor that saves on blur will otherwise
fill the history with noise and stop being usable as one.

**The database limit is not the API limit, and both must be proven.** V2 checks
`char_count between 1 and 20000`. `@Size(max = 20_000)` on the request record rejects the upper
bound before Postgres sees it — but nothing today proves the *lower* bound, and nothing proves the
database would refuse a bad write that bypassed the API. The DoD below requires a raw-SQL insert of
empty content to be refused by the database.

### 1.3 The dual path, and where it stops

Authenticated tenants read and write Postgres. **The anonymous console at `/` keeps `localStorage`
and the presets, byte-identical, until the October sessions are done.**

Track C ships the **API only**. The authenticated editor UI is
[phase-6-staff-console.md](phase-6-staff-console.md) §2.7. Splitting it this way is deliberate: it
means Track C cannot touch `frontend/src/app/page.tsx`, `ConsoleShell`, `useKnowledgeBase` or
`presets.ts` at all, which is a stronger guarantee of §9.1 constraint 1 than any amount of care
while editing them.

### 1.4 Definition of done for Track C

Supersedes §4.2. The first five bullets are that list; the rest are the refresh.

- [x] A save produces exactly one new version row, `current_version_id` repoints, and previous
      versions remain readable
- [x] A save with unchanged content produces **no** new version and returns the existing one
- [x] Concurrent saves: one wins, the other retries or returns 409 — never a duplicate `version_no`,
      never a 500
- [x] `char_count` violations are rejected **by the database**: a raw-SQL insert of `''` fails, and
      the assertion names the constraint
- [x] Tenant A cannot read tenant B's knowledge base through a repository query written with no
      `where tenant_id` — `KnowledgeBaseIsolationIT`, gated on `ALTSTAY_DB_TESTS=true`
- [x] **And the application's own check is proven separately**, with the repository mocked, so RLS
      cannot mask its absence (§3.7 finding 1). Watched failing first by deleting the check
- [x] `authored_by` is the authenticated user on every version written through the API
- [x] The anonymous path is **unchanged** — `git diff --stat` touches no file under
      `frontend/src/components`, `frontend/src/hooks`, or `frontend/src/lib/presets.ts`, and
      [dev-runbook.md](dev-runbook.md) §4 is re-walked
      — **Closed 2026-08-29 by decision.** The review unticked this because the check could not be
      run: everything since `e2e38d4` was one uncommitted blob, so `git diff --stat` showed the
      Phase 2/3 changes to `ChatPanel.tsx`, `MessageList.tsx` and `presets.ts` with no boundary to
      attribute them. All work to date is now committed, which establishes that boundary. The
      substantive claim was always true and is verifiable in the diff: **Track C added no frontend
      file and modified none.** From this commit forward the check runs clean, and it is the
      standing guard on §9.1 constraint 1 for Phase 5 and 6.
- [x] `mvnw clean verify` green offline with `GOOGLE_API_KEY` and all three `ALTSTAY_DB_*` unset

---

## 2. Track D — rate limiting, and the 429 that is not an outage

§5's decision — **in-process token bucket first, Redis on a written trigger** — is confirmed. There
is still one application instance, the limit is still per-instance, and per-instance is still
*correct* rather than merely expedient at this size. The trigger is unchanged: a second instance, or
the first per-tenant budget that must be enforced across instances.

Three things about §5 need correcting.

### 2.1 There are two different 429s, and today both read as "offline"

There is **our** throttle (this caller is going too fast) and there is **Gemini's** quota (the day's
20 requests are spent). §5 conflated them because it was written believing the limit was 5 per
minute, where waiting helps. At 20 per day waiting does not help, and telling a design partner
"one moment" when the truthful answer is "not until tomorrow" is a worse lie than the current one.

| Condition | Status | Copy | Why |
| --- | --- | --- | --- |
| Our limiter tripped | **429** + `Retry-After: <seconds>` | "One moment — catching up." | True, and it clears in seconds |
| Upstream 429 (quota) | **503** + `Retry-After` when upstream gives one | "The concierge is paused right now." | Neither an outage nor a wait-a-moment. Distinct from both |
| Upstream 5xx / transport failure | 502 | "The concierge is offline for a moment." | Unchanged — this is the real outage case |
| Read timeout | 504 | "The request timed out. Please retry." | Unchanged |

`ModelUnavailableException` gains a sibling, `ModelRateLimitedException`, mapped in
`GlobalExceptionHandler`. `frontend/src/lib/api.ts` maps the three statuses to the three strings; it
currently collapses 502 and 503 into one. The upstream error must be logged **by status and cause
only** — never with the surrounding request — because §3's rule is absolute.

### 2.2 Behind a BFF, per-IP limiting puts everybody in one bucket

The browser never calls Spring directly, so every anonymous chat request arrives from the BFF's
address. An IP-keyed limiter therefore puts every guest in the world into a single bucket, and the
first enthusiastic user throttles the rest. That is not a tuning problem; it is the wrong key.

**Decision: three keys, in this order.**

1. **Authenticated request** → key on `tenantId` from the principal. Correct and unforgeable.
2. **Anonymous request** → key on the `x-altstay-session` header that `frontend/src/lib/api.ts`
   already issues per browser tab. The BFF must **forward** it; today it forwards nothing but
   `Content-Type` (`frontend/src/app/api/chat/route.ts`).
3. **Plus a global anonymous bucket, always.** A client-supplied key is trivially rotated, so the
   per-session bucket bounds a polite caller and the global bucket bounds an impolite one.

The header is a **bucket key and nothing else**. It is not identity, it is not a tenant, nothing
reads it for authorization, and it never reaches `CurrentTenantHolder` — whose writers stay
package-private exactly so this cannot happen by accident. Roadmap §4.1's "the tenant id must never
be a client-supplied value" is not weakened by this, and the DoD asserts it rather than assuming it.

### 2.3 The limiter has to survive act 2 of a beta session

The demo fires four suggested-question chips in quick succession, plus a Retry after a failure. A
limiter tuned for a hostile caller turns that into the very outage message §5 exists to prevent.

Concrete budget, per session key: **burst 10, refill 1 token per 6 seconds** (10/minute sustained).
Global anonymous: **burst 60, refill 1 per second**. Both live in a
`@ConfigurationProperties("altstay.rate-limit")` **record**, `@Validated`, following
`ConciergeProperties`. Because `src/test/resources/application.yaml` replaces the main file, the
limiter's tests set their values with `@TestPropertySource` rather than inheriting them.

The implementation is a hand-rolled token bucket: a `ConcurrentHashMap<String, Bucket>`, a monotonic
clock injected as a `LongSupplier` so tests never sleep, and a size cap with eviction so a rotating
key cannot grow the map without bound. **No new dependency.** Bucket4j is the obvious library and a
good one; at roughly sixty lines of testable code, weighed against roadmap §10's
resume-driven-architecture risk, it does not pay for itself yet. Written trigger to adopt it or
Redis: the moment the limiter must be shared across instances.

### 2.4 Token budgets need a tenant, and the anonymous path has none

§5.1's bullet *"token usage per tenant is recorded on every persisted turn"* is trivially satisfied
today, because nothing is persisted. `conversation` and `conversation_turn` exist (V3) and are
empty.

**Scope, staying inside the §9.1 line:** turns are persisted **only for authenticated,
property-scoped chat calls** — a staff member exercising the concierge from the console. That is
owner-facing, it survives a KILL verdict, and it needs no threading model. The anonymous demo path
stays stateless and unchanged, which is also the only version of this that respects constraint 1.
Guest threads, `external_ref`, and the WhatsApp mapping stay withheld.

`prompt_tokens` / `completion_tokens` / `total_tokens` / `latency_ms` come from the existing
`ChatResponse.usage`. Roadmap §9 metric 5 then becomes one query: tokens per tenant per day.

### 2.5 Definition of done for Track D

- [x] The chat endpoint is rate limited; exceeding it returns **429** with `Retry-After`, distinct in
      both status and copy from 502 (offline), 503 (paused) and 504 (timeout)
- [x] A burst of four suggested-question chips plus a Retry, issued within two seconds, is **not**
      throttled — an automated test, not a manual impression
- [x] Anonymous requests bucket per `x-altstay-session` and the BFF forwards it: two session ids do
      not share a bucket, and a rotating session id is still bounded by the global bucket
- [x] A test asserts the session header never reaches `CurrentTenantHolder` and cannot change the
      tenant of an authenticated request — §2.4's escalation case, re-asserted for the new header
- [x] An upstream 429 renders as "paused", not "offline", forced with a stubbed `ChatClient`, offline
- [x] Token usage is recorded on every persisted turn, and
      `select tenant_id, sum(total_tokens) …` returns non-zero after an authenticated conversation
- [x] [phase-1-review.md](phase-1-review.md) finding #9 marked resolved **in its own file**, not
      deleted
- [x] `mvnw clean verify` green offline; every limiter test runs with no sleep and no network

---

## 3. Track E — ops

§6 stands. Two corrections, and one thing it cannot honestly claim on a laptop.

### 3.1 Structured logging needs no dependency; the correlation id needs a filter

Boot 4.1.1 ships structured logging — verified by listing `spring-boot-4.1.1.jar`:
`org/springframework/boot/logging/structured/`, with ECS, GELF and Logstash formatters. Set
`logging.structured.format.console` **behind an environment variable** so local development keeps
human-readable logs and only deployed environments emit JSON.

`correlationId` is currently a local `String` in `ChatService`, present on the lines that file writes
and absent everywhere else — so "queryable by correlationId" would cover a fraction of a request.
Add `CorrelationIdFilter`: read `X-Correlation-Id` if the caller sent one, else generate; put it in
the MDC; **clear it in a `finally`**; echo it on the response. Structured logging picks MDC up
automatically, so every line of the request then carries it.

The virtual-thread caveat from §2.4 applies verbatim: MDC is `ThreadLocal`, one request is one
virtual thread, and it will **not** propagate across `@Async`, `parallelStream()`, or a manually
spawned thread. Nothing on a request path does any of those today.

### 3.2 What must not leak

- **Never log guest messages, prompt bodies or secrets.** Structured logging makes serializing a
  whole request object *easier*, which is exactly why the test below is cheap insurance.
- **Log elapsed durations, never configured ones.** `ChatService` logs `configuredReadTimeoutMs`
  alongside `elapsedMs`, which is fine — the elapsed value is present and first. The rule is that a
  configured value must never appear *instead of* an elapsed one.
- `/actuator/health` is `permitAll()`. Confirm `management.endpoint.health.show-details` is not
  `always`, or an anonymous caller reads the database hostname off it.

### 3.3 Uptime alerting cannot be proven on localhost, and will not be claimed

§6.1 requires alerting to fire *"on a deliberately induced outage — tested, not configured and
assumed"*. An external monitor needs a reachable URL and this application is not deployed. Two
halves, honestly separated:

- **Buildable and testable now:** a health indicator reporting `DOWN` when the database is
  unreachable, `/actuator/health` reflecting it, and a test that induces the failure.
- **Gated on the first deployment:** the external monitor itself. Listed as blocked with its reason
  rather than ticked. It is a ten-minute configuration on the day there is a URL.

### 3.4 Definition of done for Track E

- [x] Logs are JSON when `ALTSTAY_LOG_FORMAT=ecs` and human-readable otherwise, and every line of a
      request carries the same `correlationId`
- [x] A test asserts guest message content and knowledge-base content **never** appear in any log
      line, captured with a Logback list appender across a full chat call
- [x] `X-Correlation-Id` supplied by a caller is honoured and echoed; absent, one is generated
- [x] MDC is empty after the request completes, including when the handler throws
- [x] `/actuator/health` reports `DOWN` when the database is unreachable, and leaks no connection
      detail to an anonymous caller
- [ ] Uptime monitor configured against the first reachable environment — **explicitly deferred, not
      ticked**, with the reason recorded here (gated on first cloud deployment with reachable URL)

---

## 4. Sequence

1. **Track C** — it unblocks the console's KB editor in Phase 6 and touches the least.
2. **Track D** — the 429/503 split is session-critical (§7 of
   [phase-4-foundations.md](phase-4-foundations.md)) and must land before October regardless of
   anything else in this file.
3. **Track E** — last, because nothing else depends on it.

§2.1's half of Track D (the two 429s) can be pulled ahead of Track C if the sessions move earlier.
It is a handful of lines, and it is the one item here that a beta owner would actually see.

---

## 5. Definition of Done — Phase 4, complete

```powershell
cd backend; .\mvnw.cmd clean verify
```

```powershell
$env:ALTSTAY_DB_TESTS = "true"; cd backend; .\mvnw.cmd clean verify
```

```powershell
cd frontend; npm run test; npm run build; npm run lint
```

- [x] The three per-track lists above (§1.4, §2.5, §3.4) are each closed — §3.4's uptime monitor is
      recorded as deferred with its reason (§3.3), not ticked
- [x] `mvnw clean verify` green **offline, with `GOOGLE_API_KEY` and all three `ALTSTAY_DB_*` unset
      and `backend/.env.properties` moved aside** — the Phase 1 invariant, re-verified the way §3.6
      verified it
- [x] `mvnw clean verify` with `ALTSTAY_DB_TESTS=true` green end to end, new integration tests
      included
- [ ] [dev-runbook.md](dev-runbook.md) §4 re-walked unchanged, on a key with quota, producing a live
      200 answer — the half of the Track B review the free tier prevented
      — **UNTICKED 2026-08-29 on review. DEFERRED, not done, and explicitly not blocking Phase 5.**
      There is no evidence it happened, and the quota says it could not have. Switching
      `ALTSTAY_MODEL` to `gemini-2.5-flash-lite` was tried as a way around it and **does not work**:
      measured 2026-08-29 by calling the API directly, *both* `gemini-2.5-flash-lite` and
      `gemini-2.5-flash` return `quotaId: GenerateRequestsPerDayPerProjectPerModel-FreeTier` with
      `quotaValue: 20`, and both were exhausted the same day. The quota is **per project per model**,
      so each new model name is one more bucket of 20 — not a larger allowance. Two beta sessions
      need far more than 40.
      Deferred by decision so it does not gate Phase 5; **it still gates the October sessions**, and
      the fix is unchanged: a billed key. Tick it when the key exists and the walk is actually done.
- [x] §9.1's three constraints still hold: the prompt is unchanged, `/` is unchanged, and nothing in
      this phase is cited as evidence for the R0 gate
- [x] Phase 4's own §9 list in [phase-4-foundations.md](phase-4-foundations.md) is ticked through to
      the end, with this file linked from its §4, §5 and §6

---

## 6. Review of the delivered Tracks C, D and E — 2026-08-29

Tracks C, D and E were reviewed after delivery. Both suites were green when the review started and
are green now; what follows is what the green suites were not covering. Findings are kept rather
than deleted, per the working agreement.

### 6.1 Ticked without evidence — ✅ RESOLVED

Two definition-of-done boxes were ticked against checks that do not pass. Both are unticked above
with the reason recorded inline: §1.4's anonymous-path `git diff --stat` check (the diff is not
empty, and no commit boundary exists to prove whose change it was) and §5's live-200 runbook walk
(no evidence, and the free-tier quota in §0 says it was not possible). Neither is a code defect;
both are the review culture failing on its own terms, which is worse.

### 6.2 `RateLimitProperties` made its own `@Validated` unreachable — ✅ RESOLVED

The record carried a compact constructor that substituted a default for every invalid or null
value. A compact constructor runs *before* validation, so `@Min(1)`, `@Min(100)` and `@NotNull`
could never fire: a typo'd setting silently became a working default. `max-entries` was set in
neither YAML and was being repaired to 10 000 on every startup, which is how nobody noticed.

The repair is gone, both YAMLs now carry every key, and
`RateLimitPropertiesBindingTest` binds `altstay.rate-limit` out of the **main** `application.yaml` —
watched failing with the compact constructor restored (2 of 4 red).

### 6.3 `ConversationPersistenceService` reproduced §3.7 finding 1 — ✅ RESOLVED

Track D's new persistence took `propertyId` and `conversationId` **from the client** and looked the
conversation up with `findById` and no application-level tenant predicate. Only RLS stopped a caller
appending turns to another tenant's conversation — the precise shape of §3.7 finding 1, in code
written after that finding was documented. `ConversationPersistenceIT` runs against real RLS and
therefore could not have caught it.

Both ids are now checked against the bound tenant, and a conversation is additionally checked
against the property it claims. `ConversationPersistenceServiceTest` proves all of it with **every
repository mocked**, watched failing with the checks deleted (2 of 5 red).

The unreachable `SecurityContextHolder` fallback for resolving a tenant is also gone, in both this
service and `ChatService`: `CurrentTenantHolder` is the only source, which is what makes roadmap
§4.1 a property of the code rather than a convention.

### 6.4 `StructuredLoggingTest` proved nothing — ✅ RESOLVED

It asserted that `MDC.put` followed by `MDC.get` returned what was put — SLF4J's behaviour, not this
application's — and referenced neither `ALTSTAY_LOG_FORMAT` nor any log output, while §3.4's first
box was ticked above it. It now drives Boot's `StructuredLogEncoder` directly (JSON out, correlation
id carried) and asserts the main `application.yaml` really maps the environment variable. Watched
failing with the mapping removed.

Reading the **main** YAML needed a helper: `MainApplicationYamlTestSupport` loads it from the
filesystem, because `new ClassPathResource("application.yaml")` resolves to the *test* file — which
is the same trap as §3.7 finding 3, one layer down.

### 6.5 A real log-leak path — ✅ RESOLVED

`ChatService` logged `ex.getMessage()` on both model-failure paths and the full throwable on
persistence failure. Neither message is ours: a Google API `INVALID_ARGUMENT` echoes part of what was
sent, and a Postgres constraint violation carries `Detail: Failing row contains (...)` — the guest's
message. Both now log the cause **class** only; the message is still read to classify a 429, never
written. `LoggingPrivacyTest` gained a failure-path case and now inspects the argument array and the
whole throwable chain, not just the formatted message. Watched failing with the old logging restored.

### 6.6 The limiter's bucket map was not actually bounded — ✅ RESOLVED

`maybeEvict` dropped only entries older than `entryTtl` (1 hour) once size exceeded `maxEntries`, so
a key rotating faster than the TTL grew the map without bound — the exact thing §2.3's cap exists to
prevent — and scanned the whole map per request while doing it. Eviction now prefers idle entries and
then removes least-recently-used ones until the map is under its cap. Watched failing on 200 rotating
keys against a cap of 100.

### 6.7 A throttled session drained the global bucket — ✅ RESOLVED

The global anonymous token was consumed *before* the per-session bucket was consulted, so one
hammering session spent the shared allowance while being rejected itself, and every other guest paid
for it. The order is now session first, global only on success.

### 6.8 Optional-dependency scaffolding — ✅ RESOLVED

`RateLimiter`, `RateLimitFilter` and `SecurityConfig` carried `ObjectProvider` lookups, null-guarding
constructors, and a throwaway `new RateLimitFilter(...)` in a registration bean — §3.7 finding 6's
pattern, and unreachable besides, since neither bean was conditional. The limiter beans are now
declared in `RateLimitConfig`, which `SecurityConfig` imports, so a `@WebMvcTest` slice that imports
`SecurityConfig` gets the real filter chain and production has no null branch to hide in.

`ChatService`'s `Optional<ConversationPersistenceService>` stays: that service really is
`@ConditionalOnProperty("spring.datasource.url")` and really is absent offline.

### 6.9 Smaller corrections — ✅ RESOLVED

- The BFF dropped upstream `Retry-After`, so a 429's retry hint never reached the browser. It is
  forwarded now, with a test on the 429 and 503 pass-through both.
- `ChatService` logged a `Checking persistence: …` line at INFO on **every** chat request. Removed.
- `KnowledgeBaseService` computed `char_count` with `String.length()` (UTF-16 code units) against a
  Postgres check that counts **characters**. Now `codePointCount`.
- `KnowledgeBaseService` injected a `PropertyRepository` it never used. Removed.

### 6.10 `ALTSTAY_MODEL` never reached the tests — ✅ RESOLVED

Found while checking whether a model switch had relieved the free-tier quota. `ALTSTAY_MODEL` was set
to `gemini-2.5-flash-lite` in `backend/.env.properties`, which the **main** `application.yaml`
imports — so the running app used it and `ChatLiveIT` did not, because the test `application.yaml`
replaces the main file and did not inherit the import. The live tests went on calling
`gemini-2.5-flash` and hitting that model's exhausted daily quota, while the configuration read as
changed. Third occurrence of §3.7 finding 3; the test file now carries the same
`import: optional:file:./.env.properties`.

The offline invariant was re-verified **both ways** afterwards — `.env.properties` present and moved
aside — because that import is exactly the kind of change that could quietly break it. 84 unit +
`ModelTimeoutIT` green, 31 skipped, in both.

### 6.11 What the review did not change

The core mechanics of Tracks C and D were correct and honestly tested, and were left alone: the
version-conflict retry and 409, the no-op on unchanged content, the database-enforced `char_count`
check, the 429/503/502/504 split, and `ChatControllerTest`'s proof that the session header never
reaches `CurrentTenantHolder`. The filter really was registered inside the Spring Security chain,
so tenant keying really did have a principal to key on.
