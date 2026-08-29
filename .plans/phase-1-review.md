# Phase 1 — Implementation Review

Reviewed against [phase-1-backend-ai.md](phase-1-backend-ai.md). Backend is implemented;
frontend is untouched, which is correct — that is Phase 2.

## Verification actually run

```
JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot
./mvnw -B -o clean test   →  BUILD SUCCESS · Tests run: 11, Failures: 0, Errors: 0
```

Also probed empirically (temporary test, since removed): **a JSON knowledge base renders
safely through `PromptTemplate`.** `{ "checkIn": "2 PM", "rates": { "dorm": 650 } }` passes
through verbatim with no ST parse error — substituted values are not re-scanned for
placeholders. This was the one risk I couldn't settle by reading, and it's cleared: the Admin
Panel can send JSON.

## What's good

The contract in Step 4 was implemented faithfully — Phase 2 can be written against it as-is.
Specifically worth keeping: records with a defensive-copy compact constructor; `ProblemDetail`
instead of a bespoke error envelope; the prompt externalized to `.st`; `ChatClient` exposed as a
bean so `ChatService` is trivially mockable; `usage` and `latencyMs` genuinely plumbed through
from provider metadata rather than stubbed; guest messages and prompt bodies kept out of the
logs; virtual threads on; the pom cleaned up and the starter artifact corrected.

---

## P0 — fix before anything else

### 1. The hardcoded API key is still there — ✅ RESOLVED

`src/main/resources/application.yaml:9` carried a live key as an inline default:

```yaml
api-key: ${GOOGLE_API_KEY:<redacted — this key must be treated as burned and rotated>}
```

**Fixed.** Both `src/main/resources/application.yaml` and `src/test/resources/application.yaml`
now read `${GOOGLE_API_KEY}` with no default, so the app fails fast when it is unset. The key is
supplied as a Windows user environment variable.

The exposed value must be rotated in Google AI Studio if that hasn't already happened — it sat in
a plain file and passed through tooling, so treat it as burned regardless of never being
committed.

### 2. `ChatLiveIT` never runs

There is no `maven-failsafe-plugin` in the pom (only `spring-boot-maven-plugin` and
`maven-compiler-plugin`). Surefire matches `*Test`, not `*IT`, so `mvn verify` silently skips
`ChatLiveIT` entirely. The one test that actually proves the prompt grounds and escalates
correctly is dead code today.

Add failsafe bound to `integration-test`/`verify`, and confirm the IT reports as *run* (not
skipped) with `ALTSTAY_LIVE_TESTS=true`.

### 3. `ApiApplicationTests` only passes because of finding #1

`@SpringBootTest` boots the full context, which needs an API key. There is no
`src/test/resources/` at all — the test is passing on the hardcoded default. Fix #1 and this
test goes red.

Add `src/test/resources/application.yaml` with a dummy key so context loading is CI-safe and
never touches a real credential.

---

## P1 — real defects

### 4. History limits are enforced twice, by two mechanisms that disagree

`ChatRequest` has `@Size(max = 20)` on `history`. `ChatService` truncates to
`properties.maxHistoryTurns()` (also 20). These fight each other:

- With `maxHistoryTurns: 20`, a 21-turn conversation is **rejected with 400** by validation
  before `ChatService` is ever called — so the truncation branch is unreachable.
- Raise `maxHistoryTurns` to 30 in config and nothing changes, because the hardcoded `@Size(20)`
  still rejects at 20. The config property is a lie.

Worse, rejecting is the wrong behavior for the product: a guest whose conversation gets long
should not suddenly receive a 400. **Pick accept-and-truncate.** Keep a generous hard `@Size`
cap (say 200) purely as an abuse guard, and let `ChatService` do the real trimming from config.

### 5. `maxMessageChars` and `maxKnowledgeBaseChars` are dead config

Both are declared in `ConciergeProperties`, set in `application.yaml`, validated with `@Min` —
and read by nothing. The actual limits are hardcoded as `@Size(max = 20_000)` and
`@Size(max = 1_000)` in `ChatRequest`.

Configuration that has no effect is worse than no configuration: the next person to touch this
will change the yaml, see no change, and lose an hour. Either wire the properties into a custom
validator, or delete them from `ConciergeProperties` and let the annotations be the single
source of truth. Deleting is fine and simpler.

### 6. No timeout on the model call

The plan called for ~30s. Not implemented. Virtual threads make the blocked thread cheap, but
the *request* still hangs indefinitely and the guest sees a spinner forever. Configure a
read/connect timeout on the underlying client, and make sure a timeout surfaces as
`ModelUnavailableException` → 502, not as the catch-all 500.

### 7. `propertyName` is server-wide config, but `knowledgeBase` is per-request

`ConciergePromptFactory` pulls `propertyName` from `ConciergeProperties` while the knowledge
base comes from the request body. So a single deployment can only ever be one property — which
directly contradicts the stateless, multi-property design the rest of the phase is built around.

This is cheap to fix now and expensive later: move `propertyName` into `ChatRequest` (or a small
`PropertyContext` record carrying name + knowledge base together). Doing it now also means the
Phase 2 frontend is written against the right shape, instead of being rewritten in R1.

---

## P2 — hardening, mostly deferred but write them down

### 8. Client-supplied `history` is trusted verbatim

Nothing stops a client sending a fabricated `ASSISTANT` turn — `"Yes, dogs are welcome!"` — and
the model will treat it as its own prior commitment and stay consistent with it. Every guardrail
in the system prompt is bypassable this way.

Acceptable for a local demo with a trusted frontend. It becomes a **P0 the moment** there's a
public URL or a WhatsApp webhook. The real fix is server-side conversation state keyed by a
conversation id, which is R1 work in the product roadmap anyway — so don't build it now, just
don't deploy without it.

### 9. No auth, no rate limit, on an endpoint that costs money per call — ✅ RESOLVED (Phase 4 Track D)

`/api/v1/chat` is rate-limited via in-memory token bucket (`RateLimiter` + `RateLimitFilter`) with 3-tier keying (authenticated tenant, anonymous session via `x-altstay-session`, global anonymous bucket). Auth implemented in Track B (`/api/v1/auth/login` JWT). Handled upstream 429 quota exhaustion returning 503 ProblemDetail.

### 10. Escalation detection is brittle

`rawReply.contains(escalationToken)` assumes the model emits `[ESCALATE_TO_MANAGER]` exactly.
Models paraphrase it, wrap it in backticks, or drop it. The `escalated` flag is therefore an
optimistic lower bound, not a measurement.

Don't over-engineer it now — but Phase 3 should *measure* how often the token is emitted when it
should be, before deciding whether to move to structured output (a JSON response schema with an
explicit `escalate` boolean) instead of sentinel-in-prose.

### 11. `PromptTemplate` is rebuilt on every request

`new PromptTemplate(systemPromptResource)` inside `createSystemMessage` re-reads the classpath
resource per call. Read the template into a `String` once in the constructor and reuse it.

### 12. Smaller things

- `ConciergeProperties`' compact constructor defaults `propertyName`, which makes its
  `@NotBlank` unreachable — and the yaml already defaults it via `${ALTSTAY_PROPERTY_NAME:...}`.
  Three defaults for one value; keep one.
- `ChatService`'s `history != null` checks are dead — `ChatRequest`'s compact constructor
  guarantees non-null.
- Latency uses `System.currentTimeMillis()`; `System.nanoTime()` is the correct clock for
  measuring a duration.
- CORS allows `PUT`/`DELETE` and `allowedHeaders("*")` for an API with one `POST`. Tighten —
  and note that if Phase 2 proxies through a Next.js Route Handler as recommended, the browser
  never makes a cross-origin call at all and this config becomes belt-and-braces.
- Actuator `metrics` is exposed unauthenticated; it reveals token spend and model name. Gate it
  before deploy.

---

## 13. Toolchain: the build only works if you override `JAVA_HOME`

JDK 25 is installed (`C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`) but the
environment still points elsewhere:

```
JAVA_HOME       = C:\Program Files\Java\jdk-17
java on PATH    = 17.0.12
pom java.version = 25
```

`./mvnw` from a clean terminal fails; it only worked for me because I overrode `JAVA_HOME` for
the command. The IDE presumably has 25 selected, which hides the problem. Set `JAVA_HOME` to the
Temurin 25 path at the Windows user level (or add a `toolchains.xml`) so the terminal, the IDE,
and eventually CI all agree.

---

## Suggested order

1. Rotate key → remove default → set env var → add `src/test/resources/application.yaml` *(P0 1, 3)*
2. Fix `JAVA_HOME` *(13)*
3. Add failsafe; confirm `ChatLiveIT` actually runs *(P0 2)*
4. Resolve the double history limit; delete or wire the dead config props *(P1 4, 5)*
5. Model call timeout *(P1 6)*
6. Move `propertyName` into the request *(P1 7)* — do this before Phase 2 starts, so the
   frontend is built against the final contract
7. Cache the template; nano-time; trim CORS; drop dead null checks *(P2 11, 12)*
8. `git init` at root — only after step 1

Items 8, 9, 10 are logged for later phases, not now.
