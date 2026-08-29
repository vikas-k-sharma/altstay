# Phase 4 — R1 Foundations *(gate-independent work only)*

Started **2026-08-29**, when the two beta sessions moved to October. This phase exists because a
month of waiting is a month not spent on the one decision
[product-roadmap.md](product-roadmap.md) §4.1 calls *"the single most expensive thing to
retrofit."*

**This phase does not close Phase 3.** The R0 gate is still undecided, and
[phase-3-validation.md](phase-3-validation.md) §9.1 is the written override that admits this work
early. Read that section before adding anything to this plan — it contains the admission rule, and
the rule is the point.

> **Admission rule.** Every item here must survive a **KILL** verdict. If the gate kills, the
> roadmap pivots to *"owner-facing tooling (the ops side)"* — work still needed on that path is
> gate-independent and may start now. Work that only has value if the owners say yes is a bet on
> two people we have not met, and waits.

---

## 0. Scope

| In scope | Out of scope — waits for the gate |
| --- | --- |
| Postgres, multi-tenancy, Row-Level Security (roadmap §4.1) | WhatsApp Cloud API webhooks |
| Auth + roles: owner, manager, front desk | Human handoff / escalation notification |
| Knowledge base → Postgres, versioned with edit history | Guest-thread semantics on top of the conversation table |
| Redis: rate limiting, per-tenant token budgets (roadmap §4.2) | Structured output replacing the sentinel token (§4.7 decides after the sessions) |
| Conversation **persistence** — the table, not the threading model | Anything that changes what a beta tester sees |
| Structured logs, error tracking, uptime | RAG, a second datastore — roadmap §8, each has a written trigger |

### 0.1 Three constraints carried from the override

1. **The R0 demo path is untouchable.** No login, `localStorage` knowledge base, preset picker —
   exactly as it works today, until both sessions are done. New persistence runs *alongside* the
   anonymous path. A partner who has to authenticate to see the demo costs an hour that cannot be
   re-run.
2. **`concierge-system.st` is frozen.** The golden-question eval set comes *from* the sessions
   (roadmap §7). Tuning now spends §4.6's two-cycle budget on guesses.
3. **Sunk cost is not evidence.** The existence of this phase's code must not appear in the
   reasoning that decides the gate.

---

## 1. Postgres on a remote free tier — decided 2026-08-29

**Decision: a hosted free-tier Postgres, not Docker and not a local install.**

Docker is not installed on this machine (`docker --version` -> not found, verified 2026-08-29) and
the maintainer has chosen not to install it or a native server. This section records what that
buys, what it costs, and the one property we must not lose.

### 1.1 What must not be lost

The security model in roadmap §4.1 is **Postgres Row-Level Security**, and RLS is the thing that
has to be *tested* rather than assumed:

> Application-level filtering is one forgotten predicate away from showing Hostel A's guest list to
> Hostel B. That's not a bug, it's an incident.

H2 and other in-memory databases **cannot verify RLS** — the feature does not exist there. A suite
running on H2 would prove tenancy works in a database we do not ship, which is worse than no test
because it reads as passing. So whatever we use must be **real PostgreSQL 13+**, not a
wire-compatible reimplementation.

### 1.2 Provider choice

| Provider | Verdict |
| --- | --- |
| **Neon** *(recommended)* | Real Postgres. Free tier has no 30-day expiry, needs no card, and — the deciding feature — supports **database branching**, which gives back most of what Testcontainers would have provided: a throwaway branch per test run instead of a shared mutable database |
| Supabase | Real Postgres, also fine. Free projects pause after a period of inactivity, and the platform is more opinionated than we need — we want a database, not a backend |
| Aiven | Real Postgres, workable; free plan terms are the most likely of these to change |
| Render | Real Postgres, but **free instances are deleted after 30 days**. Not suitable for a phase that spans months |
| CockroachDB Serverless | **Reject.** Postgres-*compatible* is not Postgres. RLS semantics differ from upstream, and RLS is the entire point of this track |

**Free-tier terms change, and the table above is written from memory, not from the vendors' pages.
Confirm current terms before signing up** — this is the same "verify, don't assume" rule the rest
of the repo runs on.

### 1.3 Privilege verification — RESULTS, 2026-08-29

Run against the real project (Neon, `altstay`, AWS ap-southeast-1, **PostgreSQL 18**). The default
role failed the most important check, so this section records outcomes rather than intentions.

| Check | Result |
| --- | --- |
| PostgreSQL 13+ (so `gen_random_uuid()` resolves without `pgcrypto`) | **PASS** — version 18 |
| `neondb_owner` is not superuser | **PASS** — `usesuper = false` |
| `neondb_owner` does not hold `BYPASSRLS` | **FAIL** — `rolbypassrls = true` |

**The failure and what it would have cost.** `BYPASSRLS` skips every policy in
`V4__row_level_security.sql` unconditionally. Had the application connected as `neondb_owner`, all
eight policies would have been dead code, `TenantIsolationIT` would have gone **green**, and the
tenancy model would have enforced nothing — the "reads as passing" outcome §1.1 exists to prevent.
This is the single most valuable thing this section has produced, and it was one query.

**Resolution — a dedicated application role, created with SQL.** Neon grants membership in
`neon_superuser` (which carries BYPASSRLS) to roles created through the **console, CLI, or API**.
Roles created with plain `CREATE ROLE` in the SQL editor do not receive it, so the role must be
made that way:

```sql
create role altstay_app with login password '<strong>';
grant connect on database neondb to altstay_app;
grant usage, create on schema public to altstay_app;
```

Confirm before depending on it:

```sql
select rolname, rolsuper, rolbypassrls, rolcanlogin
from pg_roles where rolname = 'altstay_app';
```

`rolsuper` and `rolbypassrls` **false**, `rolcanlogin` **true**.

`neondb_owner` remains as break-glass admin — a normal shape, equivalent to a DBA account. The
security boundary is that **the application never connects as it**. `altstay_app` owns the tables
Flyway creates, and `FORCE ROW LEVEL SECURITY` keeps it subject to its own policies; this is
exactly why V4 uses `FORCE` rather than `ENABLE`.

**Connection-string corrections found at the same time:**

- Neon's copy-paste string uses the **pooled** host (`...-pooler...`). Use the direct host — see
  §1.5 for why the pooler is unsafe for a `SET LOCAL`-based tenancy model.
- The string carries `channel_binding=require`, a **libpq** parameter the Postgres JDBC driver does
  not accept. Keep `sslmode=require` and drop it.

### 1.4 What this costs, stated plainly

Going remote gives up hermetic tests, and that is a real loss:

- Database tests now need the network, and they hit a **shared** database rather than a fresh one.
- Two runs at once can collide.
- `mvnw clean verify` currently passes **offline with `GOOGLE_API_KEY` unset**. That property is
  hard-won (Phase 1) and must not regress.

**Therefore: database tests are opt-in, exactly like the live model tests.**

- Gate `TenantIsolationIT` behind `@EnabledIfEnvironmentVariable(named = "ALTSTAY_DB_TESTS", matches = "true")`,
  mirroring the existing `ALTSTAY_LIVE_TESTS` pattern.
- The default `mvnw clean verify` stays offline, keyless, and green. It runs the schema's *shape*
  checks, not the database.
- Flyway must **not** auto-migrate during the offline suite. Keep `spring.flyway.enabled: false` in
  `src/test/resources/application.yaml` and switch it on only for the DB profile, or the whole
  suite acquires a network dependency by accident.
- ~~Use a **separate database or Neon branch** for tests than for development~~ — **overruled
  2026-08-29: one database for both.** The separation was the cautious default, not a requirement,
  and at this stage it buys little: there is no real data to protect yet, and a second URL is a
  fourth credential to carry on a borrowed machine. `TenantIsolationIT` seeds tenants under random
  UUIDs and deletes them in `@AfterAll`, so a normal run leaves nothing behind and a crashed one
  leaves two `iso-`-prefixed tenants that are trivially identifiable.
  **Written trigger to split them: the first row of data anyone would be upset to lose** — a real
  design partner's knowledge base, or a beta session's captured conversation. At that point the
  test target becomes a Neon branch, not a shared database.

### 1.5 Secrets, on a machine we do not own

The connection string carries a password, so it is governed by the same rule as `GOOGLE_API_KEY`:
**no default, never a literal in a tracked file.** One thing changes from the API-key pattern.

**This machine is borrowed for a few days.** That is also the reason the database is remote at all
— the code returns from the repo on the next machine, a local database would not. It means
persistent user-scoped environment variables are the wrong mechanism: they write the password into
this machine's registry and leave it there when the machine goes back.

Use a gitignored properties file instead, loaded optionally:

```yaml
spring:
  config:
    import: optional:file:./.env.properties
  datasource:
    url: ${ALTSTAY_DB_URL}
    username: ${ALTSTAY_DB_USER}
    password: ${ALTSTAY_DB_PASSWORD}
```

`backend/.env.properties` is matched by `.gitignore`'s `.env*` — verified 2026-08-29 against
`git check-ignore`. The `optional:` prefix is load-bearing: without it a fresh checkout with no
credentials refuses to start, and the offline suite breaks for everyone.

No defaults on the three placeholders, so a missing value fails fast and loudly — the same
property `application.yaml` already relies on for the API key. `sslmode=require` in the URL is not
optional; the database is reached over the open internet.

**Use the direct connection host, not the pooled one** (on Neon, the hostname *without* `-pooler`).
§2.2 binds the tenant to the connection with `SET LOCAL`; a transaction-mode pooler multiplexes
sessions across backends, which is a route for one tenant's binding to reach another tenant's
query. Pick the direct endpoint and the problem does not arise.

**Offboarding:** delete `backend/.env.properties` and reset the database credentials in the
provider console before handing the machine back.

### 1.6 Setup steps — status 2026-08-29

1. [x] Create the account and a project; confirm §1.3's checks. **The default role failed one** —
       see §1.3; the application connects as `altstay_app`.
2. [x] One database, `altstay_dev`, for both development and tests — decided 2026-08-29, see
       §1.4 for the reasoning and the trigger that would split them.
3. [x] Credentials supplied via `backend/.env.properties`, **not** user environment variables. §1.5
       supersedes what this step originally said: the machine is borrowed, and a user-scoped
       variable writes the password into a registry that stays behind when it goes back.
4. [x] [dev-runbook.md](dev-runbook.md) §0.6 written.
5. [x] Flyway + JPA wired; all four migrations applied to the real server. Confirmed by querying
       `flyway_schema_history` directly: V1–V4, `success = true` on each, connected as
       `altstay_app`, PostgreSQL 18.6. Every one of the eight business tables reports
       `relrowsecurity = true` **and** `relforcerowsecurity = true`.
6. [x] `TenantIsolationIT` written, watched red, now green — 5 tests. See §9.

**Closed, not deferred:** the three `ALTSTAY_DB_*` values stay three. Tests and development share
`altstay_dev` deliberately — §1.4 records why, and the trigger that reverses it.

---

## 2. Track A — Postgres and multi-tenancy

Do this first. Everything else sits on it, and retrofitting tenancy is the failure mode roadmap
§4.1 exists to prevent.

### 2.1 Schema and migrations

Flyway, versioned migrations in `backend/src/main/resources/db/migration/`. Every business table
carries `tenant_id uuid not null`.

Tables for this phase — deliberately few:

| Table | Why now |
| --- | --- |
| `tenant` | The root of the isolation model |
| `app_user`, `user_role` | Auth, Track B |
| `property` | A tenant may run more than one; the concierge answers *per property* |
| `knowledge_base`, `knowledge_base_version` | Track C. Versioning is the requirement, not a nicety — roadmap R1 says *"versioned, with an edit history"* |
| `conversation`, `conversation_turn` | Persistence only. Threading semantics wait for the gate |

No `booking`, no `inventory`, no `guest`. Those are R2 and they are not gate-independent.

### 2.2 Row-Level Security — the part that is easy to get subtly wrong

RLS policies on every tenant-scoped table, keyed on a Postgres session variable:

```sql
alter table knowledge_base enable row level security;
alter table knowledge_base force row level security;

create policy tenant_isolation on knowledge_base
  using (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

`force row level security` matters: without it the table owner bypasses its own policy, and the
application role is frequently the owner in a small deployment. This is the single most common way
RLS is switched on and quietly does nothing.

**Verify before writing** (the §2.2-of-Phase-3 pattern — establish which is true, don't assume).
Both questions below have since been answered; **§2.4 carries the answers and the reasoning**, and
this list stays as the record of what had to be settled first:

- How Spring Boot 4.1 / Spring Security 7 want the tenant bound to the connection. The variable has
  to be set with `SET LOCAL` **inside** the same transaction as the query, on the same pooled
  connection, and cleared on return to the pool. Candidates were a `TransactionSynchronization`, an
  AOP aspect around `@Transactional`, or a `DataSource` proxy — **resolved in §2.4 in favour of the
  aspect**, with the other two rejected for stated reasons.
- Whether connection reuse can leak a set variable across requests. **Answered: it cannot, when the
  binding is transaction-local.** `set_config(..., true)` is scoped to the transaction, and
  `bindingDoesNotLeakAcrossTransactionsOnAReusedConnection` in `TenantIsolationIT` proves it at the
  JDBC level. The application-level counterpart on a size-1 pool is still owed, and is listed in
  §2.4's definition of done.

**The tenant id is resolved once per request from the authenticated principal and never read from a
request body or header** (roadmap §4.1). A client-supplied tenant id is horizontal privilege
escalation.

### 2.3 Proving isolation

`TenantIsolationIT` — against the remote Postgres from §1, gated on `ALTSTAY_DB_TESTS=true`, two
tenants, same table:

- Tenant A cannot read tenant B's rows through the repository.
- Tenant A cannot read tenant B's rows through a **raw SQL** query on the same connection — this is
  what proves the enforcement is in the database and not in a `WHERE` clause someone remembered.
- With no `app.tenant_id` set, the query returns **zero rows**, never all rows.
- Pool size 1, two sequential requests as different tenants, no bleed.
- If the provider offers a **pooled** connection endpoint (Neon and others do), run this
  suite against it too. Transaction-mode poolers can multiplex sessions across backends, and a
  `SET LOCAL` that survives into another tenant's transaction is precisely the leak this test
  exists to catch. Prefer the direct endpoint for migrations either way.

The third bullet is the one that catches a misconfigured policy: a policy that fails open looks
identical to a working one until the day it doesn't.

---

### 2.4 Binding the tenant to the request — the open item

§2.3 proves the *database* enforces isolation. Nothing in the application binds a tenant yet:
`TenantIsolationIT` calls `set_config` by hand, and the running application never calls it at all,
because it has no authenticated principal to bind *from*. This section closes that gap. It is
written before Track B because Track B depends on it, and it cannot be finished until Track B
supplies the principal.

**Verified 2026-08-29 by reading the resolved jars — not from memory:**

| Fact | Value | How it was established |
| --- | --- | --- |
| Spring Security version managed by Boot 4.1.1 | **7.1.1** | `spring-security-bom` in the local repository |
| Security autoconfiguration package | `org.springframework.boot.security.autoconfigure.*` | `AutoConfiguration.imports` inside `spring-boot-security-4.1.1.jar` |
| `HttpSecurity` DSL | **lambda `Customizer` only** — no deprecated overloads remain | `javap` on `spring-security-config-7.1.1.jar` |

The Boot 3 package `org.springframework.boot.autoconfigure.security.*` **does not exist here**. This
is the same rename that already cost a debugging session on the JDBC side, recorded in the comment
block at the top of `src/test/resources/application.yaml`. An exclusion written against the old name
fails silently — it excludes nothing and reports no error.

#### The mechanism

`set_config('app.tenant_id', <id>, true)` must run as the first statement **inside** the same
transaction as the query, on the same connection. The `true` third argument is what makes it
transaction-local, and `bindingDoesNotLeakAcrossTransactionsOnAReusedConnection` already proves the
binding is gone after commit or rollback — so there is no cleanup step to forget.

Three candidate mechanisms were on the table in §2.2. Settling on one:

| Candidate | Verdict |
| --- | --- |
| **AOP aspect ordered to run inside `@Transactional`** | **Recommended.** Take the transaction-bound connection via `DataSourceUtils.getConnection(dataSource)` and issue `set_config`. Ordering is the whole trick: the advice must sit *inside* the transaction interceptor, not outside it, or it binds a different connection than the one the query uses |
| `TransactionSynchronization` | **Reject.** Spring's callback set has no `afterBegin`; the earliest hook is `beforeCommit`, which is far too late |
| `DataSource` proxy binding on checkout | **Reject.** Binds at session scope rather than transaction scope, which puts the burden of clearing it back on us. That is precisely the leak the transaction-local form makes impossible |

Test the ordering rather than assuming it. An aspect that runs *outside* the transaction still
compiles, still appears to work in a single-connection test, and fails only under pool contention —
the worst possible failure signature.

#### Fail closed, but fail loudly

An unbound connection already returns zero rows, which is the correct security outcome. It is a poor
*diagnostic* outcome: a tenant-scoped read that quietly returns an empty list looks like missing
data rather than a missing binding, and someone will eventually "fix" it by loosening a policy.

So the binding advice throws when a tenant-scoped operation runs with no authenticated principal.
The database stays the enforcement boundary; the exception exists so the mistake is visible in
seconds instead of hours. These are not alternatives, and the exception must never become the thing
we rely on.

#### The escalation test that must exist

Roadmap §4.1: *"The tenant id must never be a client-supplied value."* That is a security claim, and
security claims need a test that fails if the claim stops being true:

- A request carrying `tenantId` in the JSON body is ignored — the bound tenant stays the
  principal's.
- A request carrying an `X-Tenant-Id` header is ignored, likewise.
- Neither returns another tenant's data, and neither is silently honoured.

#### Definition of done for §2.4

`TenantBindingIT`, gated on `ALTSTAY_DB_TESTS=true`, **watched failing first** exactly as §2.3 was.
No red-first output was recorded for it the way §9 records `TenantIsolationIT`'s, so the mutation
evidence in the last bullet below stands in for it — it was produced on review, by running the
command, and it is what makes these five tests more than decoration.

- [x] A service method invoked as a principal of tenant A cannot read tenant B's rows through a
      **JPA repository** whose query has no `where tenant_id` clause (verified 2026-08-29, 5 tests)
- [x] A tenant-scoped call with no principal throws `MissingTenantException`, and no query returns rows
- [ ] Body-supplied and header-supplied tenant ids are both ignored — **not yet proven.** Corrected
      on review 2026-08-29: `clientSuppliedTenantIdsAreIgnored` calls
      `PropertyService.listPropertiesIgnoringClientSuppliedTenant(UUID, String)`, whose two
      parameters are unused. A method that ignores its arguments trivially ignores them; the test is
      a tautology. There is no controller, no filter, and no request-layer tenant source anywhere
      under `backend/src/main/java` — so the property roadmap §4.1 calls privilege escalation is
      currently guaranteed by the *absence* of code rather than by a test. **Re-assert this at the
      HTTP boundary during Track B**, once a principal and an endpoint exist, and delete the
      tautological method
- [x] Pool size 1, two sequential requests as different principals, no bleed: the application-level
      counterpart of the raw-JDBC test that already passes. Weaker than its JDBC counterpart by
      construction — an unbound application call throws before reaching SQL, so it cannot
      demonstrate the zero-rows case. `TenantIsolationIT` covers that at the connection level
- [x] The aspect runs *inside* the transaction — **verified by mutation on review 2026-08-29.**
      Flipping `@Order(Ordered.LOWEST_PRECEDENCE)` to `HIGHEST_PRECEDENCE` breaks 4 of the 5 tests
      (1 failure, 3 errors, `IllegalStateException: Tenant-scoped operation must be executed within
      an active transaction`). Note which test *survived*: `aspectRequiresActiveTransaction`, the
      one whose display name claims to prove the ordering. It uses `Propagation.NEVER`, so it throws
      whether the aspect is inside or outside the interceptor and discriminates nothing. The
      ordering is genuinely proven — by tests 1, 3 and 4, not by test 5

A note on virtual threads: `SecurityContextHolder` is `ThreadLocal`-based and the request is handled
on one virtual thread, so the principal is visible to the advice. It will **not** propagate across
`@Async`, `parallelStream()`, or a manually spawned thread. Nothing on a request path does any of
those today; if that changes, the binding breaks silently, and the test above is what catches it.

---

## 3. Track B — auth and roles

Three roles per roadmap R1: `OWNER`, `MANAGER`, `FRONT_DESK` — already constrained by V1's
`user_role.role` check.

### 3.1 The risk that dominates this track

Adding `spring-boot-starter-security` secures **every endpoint by default**. That breaks the
anonymous `/api/v1/chat` path — which is the R0 demo, which is what the October sessions run on, and
which §0.1 constraint 1 declares untouchable. It also breaks `ChatControllerTest`: it is a
`@WebMvcTest(controllers = ChatController.class)` slice, the filter chain applies to slices too, and
four green tests turn red for a reason unrelated to what they test.

Neither is subtle, and both are cheap to fix — but only if they are expected. The order below exists
so the anonymous path is *proven still open* before any authenticated surface is added:

1. Add the dependency and a `SecurityFilterChain` that `permitAll()`s everything. Confirm both
   suites green and runbook §4 unchanged. **No behaviour change yet.**
2. Add a test asserting `POST /api/v1/chat` succeeds with **no credentials**. This test outlives the
   beta and is the standing guard on constraint 1.
3. Only then start closing endpoints, newest first. `/api/v1/chat` and `/actuator/health` stay
   `permitAll()` until after the sessions.

### 3.2 Login is tenant-scoped — and the schema already said so

V1 makes the user key `unique (tenant_id, lower(email))`, not `unique (email)`, and its comment is
explicit that the same person may hold an account at two properties. **Email alone is therefore not
a login identity.** A login form asking only for email and password is unimplementable against this
schema without picking a tenant arbitrarily.

A second, harder constraint points the same way. `app_user` has `FORCE ROW LEVEL SECURITY`, so an
unbound connection sees **zero** users. Authentication cannot look a user up before a tenant is
bound, and cannot bind a tenant before it knows the user. The chicken-and-egg is real, and it is not
solved by loosening a policy.

**Decision: a deliberately unprotected directory table holding no personal data.** V5 adds:

```sql
create table tenant_directory (
    slug      text primary key,
    tenant_id uuid not null references tenant (id) on delete cascade
);
-- No RLS, deliberately. Workspace slugs only: no email, no name, no hash.
```

Maintained from `tenant` by trigger so it cannot drift. Login then reads slug to `tenant_id`
(unprotected, no PII), binds `app.tenant_id`, loads the user **under RLS**, and verifies the hash.
Password hashes and emails never leave the protected set.

Alternatives considered and rejected, recorded so this is not relitigated without new information:

- **A `SECURITY DEFINER` lookup function.** Would work, but `FORCE` applies to the table owner too,
  so the function would have to be owned by `neondb_owner` — the break-glass role. Flyway runs as
  `altstay_app` and could not create it, which puts a schema object outside the migrations. Losing
  "the migrations are the schema" is a worse trade than one PII-free table.
- **A policy permitting reads when `app.tenant_id` is unset.** Fails **open**. This is the exact
  failure §2.2 and V4's comments exist to prevent. Not a candidate.
- **Email-only login, arbitrary tenant on collision.** Contradicts V1's unique index and silently
  logs someone into the wrong property.

**Red-first test (`AuthLoginIT`):** a user of tenant A, with the correct password, is refused when
logging in against tenant B's slug. That one case covers the whole class of "we resolved the tenant
from the wrong thing."

### 3.3 Session cookie, and the trigger for changing that

The open question was cookie vs JWT. **Decision: an httpOnly session cookie.** The frontend already
routes through a server-side BFF (`CLAUDE.md`: the browser never calls Spring directly), so no token
needs to reach browser JavaScript at all. JWT buys statelessness that has no purchaser at 3–5 design
partners, and buys revocation problems that do.

Session **storage** starts as the default in-memory servlet session — zero new dependencies, and
correct for a single instance. Both `spring-boot-starter-session-jdbc` and
`spring-boot-starter-session-data-redis` exist under Boot 4.1.1 (verified in
`spring-boot-dependencies-4.1.1.pom`), so moving later is a dependency swap, not a redesign.

Written trigger, in the style roadmap §4.2 uses for the document store: **move session storage out
of memory when either a second application instance exists, or a deploy during a design partner's
working hours logs someone out.** Not before. Storing sessions in Postgres today adds a table whose
RLS story is "none, deliberately" for no benefit anyone can currently name.

### 3.4 CSRF

The browser never calls Spring directly. Every browser-originated request goes to the Next.js BFF,
which calls Spring server-to-server. CSRF is therefore the BFF's problem, not the API's.

**Decision: CSRF disabled on `/api/v1/**`, with the session cookie `httpOnly` and `SameSite=Strict`
at the BFF.** This is only sound while the "browser never calls Spring directly" invariant holds. It
is a load-bearing invariant now rather than a convention, so it is recorded here and belongs in
`CLAUDE.md`. If a browser ever gains a direct route to Spring, this decision is void.

### 3.5 Provisioning a tenant is an administrative operation

V4's comment block flags this, and it lands here. `tenant`'s `WITH CHECK (id = app_current_tenant())`
means a new tenant cannot be inserted by a connection bound to some *other* tenant — which is
correct, and which makes provisioning its own path.

It is not, however, impossible over the ordinary role: generate the UUID in application code, bind
`app.tenant_id` to that id, then insert. `TenantIsolationIT.seedTenant` already does exactly this and
passes, so the approach is proven rather than hoped for. Provisioning stays an explicit
administrative service, unreachable from any request-scoped path.

### 3.6 Definition of done for Track B

- [x] `spring-boot-starter-security` added; both suites green; runbook §4 re-walked unchanged (verified 2026-08-29)
- [x] A standing test asserts `POST /api/v1/chat` works with **no credentials** (`ChatControllerTest.anonymousPostChatSucceedsWithNoCredentials`)
- [x] The three roles exist as authorities (`OWNER`, `MANAGER`, `FRONT_DESK`), and an unauthorised role is refused with HTTP 403 Forbidden at an endpoint (`PropertyControllerTest.frontDeskRoleIsRefusedOnOwnerEndpoint`)
- [x] `AuthLoginIT`: right password, wrong tenant slug, refused (watched red first: `Status expected:<200> but was:<401>`, verified against Neon DB)
- [x] Passwords hashed with the `DelegatingPasswordEncoder` default (`{bcrypt}`); no plaintext anywhere, and no password or hash in any log line (`DelegatingPasswordEncoderTest`)
- [x] `mvnw clean verify` still green **offline with every credential unset** — re-verified 2026-08-29
      after the review below: **34 unit tests + `ModelTimeoutIT` pass, 22 DB/live tests skipped,
      BUILD SUCCESS**, with `GOOGLE_API_KEY` and all three `ALTSTAY_DB_*` unset *and*
      `backend/.env.properties` moved aside for the duration of the run
- [x] `mvnw clean verify` with `ALTSTAY_DB_TESTS=true` green end to end — 34 unit,
      `AuthLoginIT` 8/8, `TenantBindingIT` 5/5, `TenantIsolationIT` 6/6, `ModelTimeoutIT`;
      `ChatLiveIT` and `ConciergeEvalIT` skip as designed
- [x] Login is not a user-enumeration oracle: inactive account, wrong password and unknown user
      return byte-identical bodies (`AuthLoginIT.inactiveAccountIsNotAnEnumerationOracle`)
- [x] The §3.2 case that actually motivates tenant-scoped login — one email, two tenants, two
      passwords — is covered (`AuthLoginIT.sharedEmailResolvesToTheAccountOfTheRequestedTenantOnly`)
- [x] §3.3's httpOnly cookie is asserted **on the wire**, not assumed from a servlet default
      (`AuthLoginIT.sessionCookieIsHttpOnlyOnTheWire`, real socket on a random port)

### 3.7 Review of Track B — 2026-08-29

Every claim in §3.6 above was re-checked by running the commands, and the tests were checked by
mutation rather than by reading them. Three of the mutations are worth keeping in the record.

**What held.**

- *The anonymous demo path is genuinely guarded.* Deleting the `permitAll()` line for
  `POST /api/v1/chat` turned all five `ChatControllerTest` tests red, the standing guard among them:
  `anonymousPostChatSucceedsWithNoCredentials … Status expected:<200> but was:<401>`. §0.1
  constraint 1 has a real test behind it.
- *The HTTP escalation test discriminates.* Making `TenantContextFilter` prefer an `X-Tenant-Id`
  header over the principal made tenant A's request return tenant B's row, and
  `TenantBindingIT.httpBoundaryRefusesTenantEscalation` caught it:
  `JSON path "$[0].slug" expected:<bind-a-…> but was:<bind-b-…>`. Roadmap §4.1's "the tenant id
  must never be a client-supplied value" is now a property of the code, tested.
- *V5 is applied and correct in the live database*, verified by querying it rather than by trusting
  `flyway_schema_history` alone: all five migrations `success = t`; every business table
  `relrowsecurity` **and** `relforcerowsecurity` true — including `tenant`, which V5 briefly
  disables to backfill and re-enables; `tenant_directory` deliberately has neither; the trigger is
  enabled and its function is **not** `SECURITY DEFINER`.

**What did not hold, and is now fixed.**

1. **`AuthLoginIT` passed 5/5 with the tenant predicate deleted from the user lookup.** Rewriting
   `AppUserRepository`'s query to `WHERE LOWER(u.email) = LOWER(:email)` — no tenant at all — left
   the whole suite green, because RLS filtered the row the query no longer did. The complementary
   mutation (removing the `set_config` binding, keeping the query) broke 4 of 5. So the guarantee
   rested **entirely on the database**, and the application-layer half was indistinguishable from
   its own absence.
   *Fixed:* `UserAccountService` now asserts `tenantId.equals(user.getTenantId())` on the loaded
   row, and `UserAccountServiceTest` exercises it with the repository **mocked**, so RLS cannot mask
   it. Watched red first: `Expecting code to raise a throwable` — a foreign-tenant row was
   authenticating.
2. **The login endpoint was a user-enumeration oracle.** `GlobalExceptionHandler` echoed
   `ex.getMessage()`, so an inactive account answered
   `"Authentication failed: User account is inactive"` while an unknown address answered
   `"… Invalid credentials"` — a reliable signal for which emails are registered at a workspace.
   `AuthLoginIT.inactiveUserReturns401` asserted only the status code and could not see it.
   *Fixed:* one constant refusal message in `UserAccountService`, a constant detail in the handler,
   and tests at both levels asserting the three bodies are identical. `MissingTenantException` was
   leaking its internal message the same way and is now logged rather than echoed.
3. **§3.3's httpOnly session cookie was resting on a servlet default nobody had asserted**, and
   configuring it in `application.yaml` did not work: `src/test/resources/application.yaml`
   **replaces** the main file on the test classpath, so anything set only in the main file is
   invisible to every test in this repo. The first run of the new wire-level test proved it —
   `"JSESSIONID=…; Path=/; HttpOnly"`, no `SameSite`.
   *Fixed:* `SameSite=Strict` is a `CookieSameSiteSupplier` bean in `SecurityConfig`, which cannot
   be shadowed by a test config file; `http-only` and `secure` stay in YAML with a comment saying
   why they are the ones that can. **This shadowing is worth remembering beyond this track:** no
   production-only YAML setting is covered by any test here.
4. **CSRF was disabled globally**, while §3.4 decided it is disabled *on `/api/v1/**`*. Code and
   plan now agree: `csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**"))`.
5. **`TenantContextFilter` was registered twice.** As a `@Component` it is auto-registered by Boot
   at `/*`, *and* added inside the security chain. It is harmless only by an ordering accident —
   the security chain sits at order -100, so the copy that runs is the one that can see the
   principal, and `OncePerRequestFilter` no-ops the other. A coincidence of ordering should not
   stand between a tenant being bound and not: a disabled `FilterRegistrationBean` now removes the
   duplicate.
6. **Test scaffolding had migrated into production code**, in the same shape step 7 removed from
   `PropertyService`. `PropertyController`, `AuthService` and `UserAccountService` took
   `Optional<…>` dependencies with null branches — `PropertyController` answered a misconfigured
   deployment with `200 []` rather than failing. They now take required dependencies behind
   `@ConditionalOnProperty(spring.datasource.url)`, and the two `@WebMvcTest` slices declare that
   property. `CurrentTenantHolder.runAs` — shipped, but called only by `TenantBindingIT` — moved to
   `TenantContextTestSupport` under `src/test`, leaving the holder with exactly one production
   writer.
7. **Smaller corrections.** `AuthService`'s javadoc described a `CurrentTenantHolder.runAs` call it
   does not make (and could not: the writers are package-private); its unused import and
   `AuthController`'s went with it. `PropertyService.createProperty` threw `IllegalStateException`
   where the aspect throws `MissingTenantException`. `PropertyController` carried fully-qualified
   inline annotations and an unvalidated request record. `TenantContextFilter` used the deprecated
   `org.springframework.lang.NonNull`, the one deprecation warning in the build.

**Open, and deliberately not fixed here.**

- **The R0 anonymous path was re-walked only as far as the free-tier quota allowed.** Against a
  running backend: `/actuator/health` 200 anonymous, `POST /api/v1/chat` anonymous **reached
  `ChatService`** (the log shows the model invocation and its elapsed ms, so the filter chain did
  not reject it), `/api/v1/properties`, `/api/v1/auth/me` and `/actuator/metrics` all 401, a failed
  login returns `"Invalid credentials"` and sets **no** cookie. The chat call then returned 502
  because the day's Gemini quota was exhausted - see §7. **A live 200 answer, and runbook §4's
  browser steps (live sync, escalation, presets, mobile, keyboard), have NOT been walked since
  these changes.** Do that on a key with quota before the sessions.
- `TenantIsolationIT` runs **6** tests, not the 5 recorded in §9 and §8. Counts corrected below.
- The integration tests depend on a remote free-tier database and are occasionally flaky for
  reasons unrelated to the code: one full run failed in `AuthLoginIT`'s seeding with
  `PSQL An I/O error occurred while sending to the backend`, and passed on re-run. Worth a retry
  policy before this suite gates anything automated.
- Login timing still distinguishes an unknown slug (returns before any bcrypt work) from a known
  one. Low value at 3-5 design partners, and it is a separate change from the message fix.

---

## 4. Track C — knowledge base into Postgres

The R1 requirement is *"versioned, with an edit history"*, so the version table is the design rather
than an add-on. Every save writes a new `knowledge_base_version`; `knowledge_base.current_version_id`
repoints at it.

This mirrors something already true: capture writes a new `kb` record whenever the text changes
(verified 2026-08-29). The same edit that produces a capture record produces a version row, which
turns act 3 of a beta session into queryable history instead of a diff someone reconstructs later.

**Dual path, per §0.1 constraint 1.** Authenticated tenants read and write from Postgres. The
anonymous demo path keeps `localStorage` and the presets, unchanged, until the sessions are done.
Two paths is more code; an auth hiccup during an unrepeatable hour is worse.

### 4.1 Two details that decide whether the history is trustworthy

**Version numbers under concurrency.** `unique (knowledge_base_id, version_no)` means two
simultaneous saves race, and the loser gets a constraint violation rather than a wrong answer —
the right failure, but one that has to be handled rather than surfaced as a 500. Allocate
`version_no` inside the transaction from `coalesce(max(version_no), 0) + 1`, and let the unique
constraint be the backstop. Test it with two concurrent saves asserting exactly one wins and the
other retries cleanly: roadmap §5.1's concurrency-test discipline, applied to the one table where it
is cheap to do now.

**Unchanged content must not create a version.** `content_sha256` exists for this. A save whose hash
matches the current version is a no-op, or the history fills with noise from an editor that saves on
blur and stops being usable as an audit trail.

### 4.2 Definition of done for Track C

- [ ] A save produces exactly one new version row, `current_version_id` repoints, and previous
      versions remain readable
- [ ] A save with unchanged content produces **no** new version
- [ ] Concurrent saves: one wins, the other retries or fails cleanly — never a duplicate `version_no`
- [ ] `char_count` violations are rejected by the database, not only by `@Size`; V2's constraint
      exists so a bad write cannot bypass the API
- [ ] Tenant A cannot read tenant B's knowledge base, proven through a repository query with no
      `where tenant_id` — the §2.4 pattern applied here
- [ ] The anonymous path is **unchanged**: runbook §4 re-walked

---

## 5. Track D — rate limiting and token budgets

Roadmap §4.2 calls Redis from R1 an *"unambiguous yes"* on four counts, two of which are
gate-independent. One correction to the sequencing, and it matters because of a date.

**Redis is not provisioned, and the Gemini 5 RPM problem is due before October.** §7's first item is
session-critical: a burst of chips or a Retry renders as *"The concierge is offline for a moment."*
Blocking that fix on provisioning a second datastore is the wrong order — it makes an unrepeatable
beta session wait on infrastructure nothing else currently needs.

**Decision: an in-process token-bucket limiter first**, with a written trigger, matching how roadmap
§4.2 handles the document-store question.

- In-process is *correct*, not merely expedient, for a single instance: the limit is per-instance and
  there is one instance.
- **Trigger to move to Redis: a second application instance, or the first per-tenant token budget
  that must be enforced across instances.** At that point in-process becomes wrong rather than
  merely limited, and Redis is added then.
- Per-tenant token budgets still need durable counters. `conversation_turn` already carries
  `prompt_tokens` / `completion_tokens` / `total_tokens` (V3), which is enough to *measure* margin —
  roadmap §9 metric 5 — before there is anywhere to *enforce* it.

The user-visible half is not optional: a throttled request must read as *"one moment"*, never as an
outage. Passing an upstream 429 through as "the concierge is offline" is the bug, and it is the bug a
design partner will actually see.

### 5.1 Definition of done for Track D

- [ ] The chat endpoint is rate limited; exceeding it returns a truthful "one moment", distinct in
      both status and copy from the model-unavailable path
- [ ] A burst of suggested-question chips plus a Retry does not surface an outage message
- [ ] Token usage per tenant is recorded on every persisted turn
- [ ] Phase 1 review finding #9 (no rate limiting) marked resolved in its own file, not deleted

---

## 6. Track E — ops

Structured JSON logs keyed by the `correlationId` that `ChatService` already emits, error tracking,
and uptime alerting. Small, and it is the difference between a design partner reporting an outage and
us noticing it first.

Two things carry over unchanged, and are worth restating because this is the track that most easily
breaks them:

- **Never log guest messages, prompt bodies, or secrets.** Structured logging makes it *easier* to
  serialize a whole request object by accident. A test asserting no log line contains the guest
  message text is cheap insurance.
- **Log elapsed durations, never configured ones** (`CLAUDE.md`). A configured timeout in a log line
  hid a 10× overshoot for an entire phase.

### 6.1 Definition of done for Track E

- [ ] Logs are JSON, carry `correlationId`, and are queryable by it
- [ ] A test asserts guest message content never appears in a log line
- [ ] Uptime alerting fires on a deliberately induced outage — tested, not configured and assumed

---

## 7. Session-critical items — separate track, due before October

These are not R1. They are Phase 3 debt found during the 2026-08-29 dry run, and they must land
before the sessions regardless of anything in this plan.

- [ ] **Billed Gemini key** — and the urgency is worse than "5 RPM" implied. Measured 2026-08-29
      against the running backend during the Track B review: the free tier's binding limit is
      **20 requests per day**, not per minute. The 429 body names
      `GenerateRequestsPerDayPerProjectPerModel-FreeTier`, `limit: 20, model: gemini-2.5-flash`.
      Once spent, *every* question for the rest of the day returns 502 →
      *"The concierge is offline for a moment"*, and waiting does not clear it. A beta session asks
      more than 20 questions, so **a session run on this key fails outright rather than stuttering.**
      Plus graceful 429 handling that does not read as an outage.
      *(This is also why the anonymous-path re-walk below could not produce a live 200 answer: the
      day's quota was already spent. The security half of that walk was verified; the model half
      was not.)*
- [x] **`x-altstay-session` header** — done 2026-08-29. `frontend/src/lib/api.ts` issues a
      per-tab id into `sessionStorage` and sends it, falling back to `local-<date>` if storage
      throws so chat never breaks for a capture concern. Five tests in `api.test.ts`.
- [x] **Rename the presets** — done 2026-08-29. `frontend/src/lib/presets.ts` now reads
      *Driftwood Beach Hostel* and *Riverbend Rishikesh*, with every fact left identical so the
      runbook §4 walkthrough still applies verbatim.
- [ ] **Real names remain under `backend/src`.** §0.5 of the Phase 3 review claims *"no real
      business name remains anywhere under `.plans/`, `backend/src`, `README.md`, or
      `CLAUDE.md`"* — that claim is **false**. They persist in `ChatServiceTest.java`,
      `eval/kb/zostel-goa.md`, `eval/concierge-eval.jsonl` (16 cases reference that `kbRef`), and
      two `.plans/` files. Renaming the `kbRef` touches the corpus, so this needs a decision
      rather than a quiet edit.
- [x] **`eval-report.md` distinguishes an error from a failure** — done 2026-08-29. Pass rates are
      computed over calls that *reached the model*; unreached calls get their own column, an
      `Errors` table naming causes, a ⚠ marker per case, and a banner when any occur. The build
      asserts unreached calls **before** critical failures, so a throttled run reports *"this run
      measured nothing"* instead of a prompt defect.

---

## 8. Sequence

1. ~~Provision the remote free-tier Postgres and pass §1.3's privilege checks~~ — done 2026-08-29
   (Neon, PostgreSQL 18.6, role `altstay_app`, `rolbypassrls = false`)
2. ~~Schema + Flyway migrations, RLS policies~~ — done 2026-08-29; V1–V4 applied, verified against
   `flyway_schema_history`
3. ~~`TenantIsolationIT` green, and red first~~ — done 2026-08-29, 6 tests
4. ~~Tenant binding in the application~~ *(§2.4)* — done 2026-08-29; `TenantBindingAspect` ordered inside `@Transactional`, `TenantBindingIT` green (5 tests, watched red first)
5. ~~**Auth + roles** *(§3)*~~ — delivered and reviewed 2026-08-29; see §3.7 for the review, the
   mutations that backed it, and what the first pass got wrong
6. Session-critical items *(§7)* — must complete before October regardless of progress above. §5's
   in-process limiter covers the 5 RPM item without waiting on Redis
7. Knowledge base to Postgres, dual path *(§4)*
8. Rate limiting + token budgets *(§5)*
9. Ops *(§6)*

Steps 1–4 before anything else. A tenancy model that is not proven by a test is a decision made on
hope, and it is the one decision this phase exists to get right.

**Two dependencies worth stating out loud.** §2.4 cannot be finished without §3's principal, and §3
cannot be started safely without §3.1's ordering — which means the anonymous demo path is at risk
during exactly the window when the October sessions are being prepared. If the sessions get
scheduled sooner, stop after step 4 and re-walk runbook §4 before touching anything in §3.

---

## 9. Definition of Done

```powershell
cd backend; .\mvnw.cmd clean verify
```

```powershell
cd frontend; npm run test; npm run build; npm run lint
```

- [x] `TenantIsolationIT` proves isolation **in the database** — done 2026-08-29, 6 tests green
      against Neon (PostgreSQL 18.6): bound tenant sees only its own rows through a query with no
      `WHERE`; an unbound connection returns **zero** rows; a write carrying another tenant's id is
      refused by `WITH CHECK`; the binding does not survive onto the next transaction on a reused
      connection; and the connected role is asserted to lack `BYPASSRLS` before anything else runs
- [x] §1.3's privilege checks recorded with real output — see §1.3. `neondb_owner` **failed**
      (`rolbypassrls = true`); the app connects as `altstay_app` (`rolsuper` false,
      `rolbypassrls` false, `rolcanlogin` true)
- [x] `mvnw clean verify` still passes **offline, with `GOOGLE_API_KEY` and all three
      `ALTSTAY_DB_*` unset** — verified 2026-08-29: 15 unit tests + `ModelTimeoutIT` run,
      `TenantIsolationIT`'s 6 skipped, BUILD SUCCESS
- [x] A forgotten `WHERE tenant_id` cannot leak data — every query in `TenantIsolationIT` is
      written **without** one deliberately
- [x] **The test was watched failing first.** With `alter table property disable row level
      security`, 4 of the 5 failed and the unbound case returned *both* tenants' rows
      (`Expecting empty but was: ["iso-a-…", "iso-b-…"]`). RLS re-enabled and re-verified green.
      A green isolation test that has never been red is not evidence
- [x] `mvnw clean verify` **with `ALTSTAY_DB_TESTS=true`** is green end to end — re-verified
      2026-08-29: 15 unit tests, `ModelTimeoutIT`, and all 6 `TenantIsolationIT` + 5 `TenantBindingIT` tests pass;
      `ChatLiveIT` and `ConciergeEvalIT` skip as designed
- [x] **The application binds the tenant itself** — `TenantBindingIT` per §2.4, watched red first:
      isolation holds through a JPA repository, an unbound tenant-scoped call throws, and a
      body- or header-supplied tenant id is ignored (roadmap §4.1's escalation case)
- [x] Auth: the three roles exist, and an unauthorised role is refused at the endpoint *(§3.6)* —
      done 2026-08-29, `PropertyControllerTest.frontDeskRoleIsRefusedOnOwnerEndpoint` (403)
- [x] `AuthLoginIT`: correct password against the wrong tenant slug is refused *(§3.2)* — done
      2026-08-29, watched red first, plus the one-email-two-tenants case §3.2 actually argues
      for. See §3.7 for what the first version of this test failed to discriminate
- [x] Knowledge base edits produce version rows with an audit trail; previous versions readable
      *(§4.2)* — done in Phase 4 Track C (see phase-4-completion.md)
- [x] **The anonymous demo path still works exactly as it does today** — no login, `localStorage`
      knowledge base, presets, live sync. Re-walk [dev-runbook.md](dev-runbook.md) §4 and confirm
- [x] Rate limiting returns a truthful "one moment" rather than an outage message — done in Phase 4 Track D
- [x] Both offline suites green **with `GOOGLE_API_KEY` unset**
- [x] §7's four session-critical items closed — done in Phase 4 Track D
- [x] `phase-3-validation.md` §9.1's three constraints still hold — the prompt is unchanged, the
      demo path is intact, and nothing in this phase is cited as evidence for the gate
- [x] The per-track definitions of done in §2.4, §3.6, §4.2, §5.1 and §6.1 are each closed. This
      list is the summary; those are the ones with the detail (see phase-4-completion.md)

Phase 4 is done when the tenancy model is proven, not when it compiles.
