# AltStay API

Spring Boot service behind the AltStay concierge. One endpoint: it takes a property's knowledge
base, the conversation so far, and a guest question, and returns a grounded answer from Gemini.

Part of the [AltStay OS monorepo](../README.md).

## Stack

| | |
| --- | --- |
| Java | 25 (Temurin) |
| Spring Boot | 4.1.1 — servlet stack, virtual threads enabled |
| Spring AI | 2.0.1 (`spring-ai-starter-model-google-genai`) |
| Model | `gemini-2.5-flash` (configurable) |
| Build | Maven wrapper |

No database. No security layer. Both are R1 — see [Limitations](#limitations).

## Design

**Stateless by design.** The service holds no conversation state; every request carries its own
knowledge base and history. That means horizontal scaling with no session affinity, and a rule
edit taking effect on the very next message with no restart or cache invalidation.

The cost is prompt tokens and bandwidth, which is why history is truncated server-side and
payload sizes are capped.

When the knowledge base later moves to Postgres (R1), only *where* it is sourced from changes —
the API contract holds.

### Package layout

Package-by-feature, not by layer, so each vertical slice stays self-contained as `property/` and
`booking/` arrive later.

```
com.altstay.api
├─ ApiApplication.java
├─ chat/
│  ├─ ChatController.java          bind, validate, delegate — no logic
│  ├─ ChatService.java             truncate history, build prompt, call model, parse result
│  ├─ ConciergePromptFactory.java  renders the system prompt template
│  └─ dto/                         records: ChatRequest, ChatResponse, ChatTurn, Role, TokenUsage
├─ common/
│  ├─ GlobalExceptionHandler.java  RFC 9457 ProblemDetail responses
│  └─ ModelUnavailableException.java
└─ config/
   ├─ ChatClientConfig.java        exposes ChatClient as a bean (keeps ChatService mockable)
   ├─ ConciergeProperties.java     @ConfigurationProperties record
   ├─ CorsProperties.java
   └─ WebConfig.java               CORS from config, not annotations
```

DTOs are Java records with compact constructors for defensive copying. Lombok is used only for
service-layer conveniences (`@Slf4j`, `@RequiredArgsConstructor`) — never on a DTO.

### The prompt

Lives in `src/main/resources/prompts/concierge-system.st`, not in Java. It's the file that gets
iterated on hardest, and keeping it external means editing it is a diff rather than a recompile.

Two rules in it carry the product:

1. Answer **only** from the knowledge base.
2. If the answer isn't there, don't guess — escalate, and emit an escalation token.

`ChatService` detects that token, strips it from the reply, and sets `escalated: true`. The token
is injected at render time rather than written in the template, so a guest can't read it out of a
leaked prompt and spoof an escalation.

## API

### `POST /api/v1/chat`

```json
{
  "propertyName": "Zostel Plus Goa",
  "knowledgeBase": "## Check-in\nFrom 2:00 PM. Dorm bed ₹650/night. No pets.",
  "history": [
    { "role": "USER",      "content": "do you have dorms?" },
    { "role": "ASSISTANT", "content": "Yes! 6-bed mixed dorms at ₹650/night." }
  ],
  "message": "can I bring my dog?"
}
```

`200 OK`:

```json
{
  "reply": "We're not able to host pets, sorry!",
  "escalated": false,
  "model": "gemini-2.5-flash",
  "usage": { "promptTokens": 412, "completionTokens": 58, "totalTokens": 470 },
  "latencyMs": 823
}
```

`usage` and `latencyMs` are returned deliberately — per-message cost and latency are how gross
margin per tenant gets measured later, and the UI surfaces them during tuning.

**Limits** (400 on breach): `knowledgeBase` ≤ 20,000 chars, `message` ≤ 1,000 chars, `history`
≤ 200 turns. History is additionally truncated to the newest `max-history-turns` before the model
call, so a long conversation degrades rather than failing.

### Errors — RFC 9457 `application/problem+json`

| Status | `type` suffix | When |
| --- | --- | --- |
| 400 | `validation-error` | Bean validation failed; `errors` names the fields |
| 502 | `model-unavailable` | Upstream model call failed |
| 500 | `internal-error` | Anything unhandled |

Provider exception messages never reach the client — they can echo prompt fragments.

### Actuator

`/actuator/health`, `/actuator/info`, `/actuator/metrics` only. Spring AI publishes Micrometer
observations for chat calls, so token usage and latency show up in metrics for free.

## Running

Needs **JDK 25** on `JAVA_HOME`.

```powershell
.\mvnw.cmd spring-boot:run
```

Starts on port 8080 in roughly 1.3s. Full environment setup:
[`../.plans/dev-runbook.md`](../.plans/dev-runbook.md).

Smoke test:

```powershell
curl.exe -s http://localhost:8080/actuator/health
```

> In PowerShell use `curl.exe` — plain `curl` is an alias for `Invoke-WebRequest` and rejects
> these flags.

## Configuration

`src/main/resources/application.yaml`.

| Key | Default | Purpose |
| --- | --- | --- |
| `ALTSTAY_MODEL` | `gemini-2.5-flash` | Model id |
| `ALTSTAY_PROPERTY_NAME` | `AltStay Property` | Fallback when the request omits it |
| `ALTSTAY_ALLOWED_ORIGINS` | `http://localhost:3000` | CORS origins |
| `altstay.concierge.max-history-turns` | `20` | Turns kept before the model call |

`GOOGLE_API_KEY` is required and has no default — the app fails fast at startup if it is unset.
Supply it as an environment variable; never put the literal key in a tracked file. On Windows, set
it once for your user account:

```powershell
[Environment]::SetEnvironmentVariable('GOOGLE_API_KEY', '<your-key>', 'User')
```

Then open a new terminal (and restart your IDE) so the value is inherited.

## Tests

```powershell
.\mvnw.cmd clean verify
```

12 tests, all offline, no API key needed:

- `ChatServiceTest` — history truncation, prompt assembly, escalation token stripping, provider
  failure mapping
- `ChatControllerTest` — status codes and problem+json shapes
- `ApiApplicationTests` — context loads (uses `src/test/resources/application.yaml`)

`ChatLiveIT` calls the real model and is skipped unless opted in. It's the only test that proves
the prompt actually grounds and escalates — run it before demoing:

```powershell
$env:ALTSTAY_LIVE_TESTS="true"; .\mvnw.cmd verify
```

## Limitations

Known and tracked in [`../.plans/phase-1-review.md`](../.plans/phase-1-review.md):

- **No authentication or rate limiting.** The endpoint is open and costs money per call.
- **No timeout on the model call** — a hung upstream parks the request indefinitely.
- **Client-supplied history is trusted**, so a caller can fabricate assistant turns and talk the
  model past its guardrails. Server-side conversation state (R1) is the fix.
- Escalation detection is substring matching on the token; models sometimes paraphrase it, so
  `escalated` is a lower bound until Phase 3 measures it.

## Conventions

- Never log guest messages, prompt bodies, or secrets. One structured line per chat call:
  correlation id, model, tokens, latency, escalated.
- Controllers stay thin; `@RestControllerAdvice` owns error mapping.
- Config through `@ConfigurationProperties` records — no scattered `@Value`.
