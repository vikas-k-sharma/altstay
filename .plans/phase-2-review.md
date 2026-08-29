# Phase 2 — Implementation Review

Reviewed against [phase-2-frontend.md](phase-2-frontend.md). The API key was hardcoded at the
time of this review and excluded from it by request; it has since been moved to the
`GOOGLE_API_KEY` environment variable.

## Verification actually run

```
npm run test   →  2 files, 8 tests passed
npm run build  →  clean; TypeScript clean; ○ / static, ƒ /api/chat dynamic
./mvnw spring-boot:run  →  Tomcat on 8080, started in 1.271s on Java 25
```

## What's good

The plan's three load-bearing decisions all landed. The BFF route handler is genuinely well
built — it validates inbound with zod, forwards upstream problem+json unchanged, validates the
*response* before returning it (with a distinct `upstream-contract-mismatch` 502 when the backend
drifts), and separates timeout (504) from unreachable (502). That response-validation step is the
one people skip, and it's here.

Also right: `BACKEND_URL` is server-only with no `NEXT_PUBLIC_` prefix, so the backend never
appears in the browser. `MessageList` has `role="log"` + `aria-live="polite"` and only autoscrolls
when already near the bottom. Failed sends preserve the user's text and expose retry. `escalated`
and the token/latency metadata are both surfaced in the UI, so the fields the backend went to the
trouble of returning are actually used.

**The backend contract was updated to match** — `ChatRequest` now carries `propertyName` and
`@Size(max = 200)` on history. That closes review items #7 and #4 from Phase 1, and the frontend
zod schema mirrors it exactly.

---

## 1. Hydration mismatch on the knowledge base — ✅ RESOLVED

`src/hooks/useKnowledgeBase.ts:21-29` reads `localStorage` inside the `useState` initializers via
`getInitialValue()`.

`page.tsx` is a client component, but client components are still prerendered — the build output
confirms it (`○ /` = static). So:

- **Server/build render:** `typeof window === 'undefined'` → returns `DEFAULT_PRESET`.
- **Client's first render:** reads `localStorage` → returns the saved knowledge base.

Different trees for the same render pass. React logs a hydration mismatch and discards the
server HTML.

What makes this nasty is *when* it shows up: a fresh browser has empty `localStorage`, so both
sides agree and everything looks fine. It only breaks **after someone edits the rules and
refreshes** — which is precisely what a beta tester does, and precisely the Phase 2 DoD item
"page refresh preserves the knowledge base."

**Fixed**, and then re-implemented: the hook now uses `useSyncExternalStore`, where React uses
`getServerSnapshot` for the client's hydration render as well as the server's, so both trees
match by construction and storage is read only afterwards.

**Two constraints that must hold on `getServerSnapshot`, both of which have already broken once:**

- It must return a **stable reference** (a frozen module constant, not a fresh object literal).
  React compares snapshots with `Object.is`; a new object per call reads as a perpetual store
  change and triggers *"The result of getServerSnapshot should be cached to avoid an infinite
  loop."*
- It must return the **pre-`localStorage` defaults**. Reading storage there reintroduces this
  exact mismatch — invisibly, because it only manifests once a user has saved something.

`suppressHydrationWarning` was not used and should not be: it hides the symptom while leaving the
two trees genuinely divergent.

---

## 2. Smaller findings

**`react-hook-form` and `@hookform/resolvers` are installed but never used.** `AdminPanel` uses
plain controlled `onChange` handlers. That's a deviation from the plan — but honestly the *plan
was wrong* here: for two fields with no submit step, controlled state is simpler and correct, and
react-hook-form would be ceremony. Keep the implementation, **drop the two dependencies.**

**Over-limit knowledge base doesn't block sending.** `RulesEditor` turns the counter red past
20,000 chars, but nothing stops the send; the BFF's zod rejects it and it surfaces as an error
bubble in the chat. The plan wanted it caught inline before the request. Minor, but the current
behavior puts an admin-panel error in the chat transcript, which reads oddly.

**`page.tsx` is `'use client'`,** so the entire tree is client-rendered. The plan wanted
`'use client'` at the leaves. It works and it's why `/` prerenders as static — but it means
there's no server component boundary to hang server-fetched data on, which R1 will want when the
knowledge base moves to Postgres. Cheap to fix now: keep `page.tsx` a server component and lift
the two hooks into a `ConsoleShell` client component.

**`useTransition` is doing bookkeeping twice.** `useConversation` destructures
`const [, startTransition]` — discarding `isPending` — while separately tracking a `status`
state. The transitions aren't buying anything here since these updates aren't competing with
urgent input. Dropping `useTransition` and keeping `status` would be strictly simpler.

**Route handler sets `content-type: application/json` on error responses**, not
`application/problem+json`. Cosmetic, but the backend gets this right and the proxy flattens it.

**Two test gaps** the plan called for: that history mapping strips UI-only fields (`id`,
`timestamp`, `meta`) before sending — currently untested and easy to regress into sending the
whole `UiMessage` — and the 504 timeout branch of the route handler.

---

## 3. Still open from Phase 1

Not Phase 2's fault, but they gate a deploy:

- **The backend still has no timeout on the model call** (Phase 1 review #6). The BFF's 25s
  `AbortSignal.timeout` masks it in the browser, but the backend request thread stays parked.
- No auth or rate limiting on either `/api/chat` or `/api/v1/chat` (Phase 1 review #9).
- ~~`ChatLiveIT` needs `maven-failsafe-plugin`~~ — resolved; runs under `ALTSTAY_LIVE_TESTS=true`.

---

## 4. Suggested order

1. ~~Fix the hydration mismatch~~ *(#1)* — done
2. ~~Drop the unused `react-hook-form` deps~~ *(#2)* — done
3. ~~Add the two missing tests~~ *(#2)* — done, suite is now 10
4. ~~Server-component `page.tsx`~~ — done via `ConsoleShell`
5. **Backend model-call timeout** *(#3)* — **still open, the last real gap**
6. Optional: drop `useTransition`, problem+json content type on proxied errors

Then run the browser checks in [dev-runbook.md](dev-runbook.md) §4 and move to Phase 3.
