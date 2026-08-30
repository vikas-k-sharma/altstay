# Dev Runbook — Running the Full Stack Locally

Re-walked end to end on this machine **2026-08-29**, as the dry run required by
[phase-3-validation.md](phase-3-validation.md) §5.1. Backend starts on Java 25 / port 8080 in
~1.2s; `mvnw clean verify` green (15 unit tests + `ModelTimeoutIT`; 3 live tests skipped without
`ALTSTAY_LIVE_TESTS`); frontend `npm run test` green (12 tests).

> **The PowerShell `curl.exe` payloads in §3 were wrong and have been replaced.** Windows
> PowerShell 5.1 strips the quotes out of an inline JSON `-d` argument, so every §3 command whose
> body contained a space failed with `400 Failed to read request`. Worse, §3.5 still *returned* 400
> — for the wrong reason — so it read as a pass while testing nothing. §3 now uses
> `Invoke-RestMethod`, which takes a JSON body without any quoting games.

You need **two terminals**: one for the backend, one for the frontend.

---

## 0. One-time setup

### 0.1 Point `JAVA_HOME` at JDK 25

The pom targets Java 25 and Temurin 25 is installed, but `JAVA_HOME` still points at JDK 17.
Without this the backend build fails with an invalid-target error.

```powershell
[Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot', 'User')
```

### 0.2 Put Node on PATH

Node 24.20.0 is installed at `C:\Program Files\nodejs` but is not resolvable from every shell.

```powershell
[Environment]::SetEnvironmentVariable('PATH', "$([Environment]::GetEnvironmentVariable('PATH','User'));C:\Program Files\nodejs", 'User')
```

**Close and reopen every terminal after 0.1 and 0.2** — environment variables are read at shell
start.

### 0.3 Confirm

```powershell
java -version; node -v; npm -v
```

Expect Java 25.x, Node v24.20.0, npm 11.x. If Java still says 17, the terminal didn't reload.

### 0.4 Frontend env

`frontend/.env.local` already exists and contains `BACKEND_URL=http://localhost:8080`. Nothing to
do. It is server-only (no `NEXT_PUBLIC_` prefix), so it never reaches the browser — which is the
point of the BFF. `.gitignore` covers `.env*`.

### 0.5 Gemini API key

`GOOGLE_API_KEY` has no default in `application.yaml` — the backend fails fast at startup if it
is unset. Set it once for your user account:

```powershell
[Environment]::SetEnvironmentVariable('GOOGLE_API_KEY', '<your-key>', 'User')
```

Reopen the terminal (and restart your IDE) afterwards so the value is inherited. Never put the
literal key in a tracked file.

### 0.6 Postgres — remote, not local *(Phase 4)*

Decided 2026-08-29: the database is a **hosted free-tier Postgres** (Neon recommended). Two
reasons, and the second is the stronger one:

1. Docker is not installed here and is not going to be.
2. **This machine is borrowed.** The code comes back from the repo on the next machine; a local
   database would not. Hosting it means the only thing that has to travel is a connection string.

Provider comparison and the reasoning are in [phase-4-foundations.md](phase-4-foundations.md) §1.

#### Credentials on a machine you do not own

**Do not use `SetEnvironmentVariable(..., 'User')` for the database password here.** That writes it
into this machine's registry and leaves it behind when you hand the machine back. Use a gitignored
file the app reads at startup instead, and delete it when you are done.

Create `backend/.env.properties` — matched by `.gitignore`'s `.env*`, so it cannot be committed:

```properties
ALTSTAY_DB_URL=jdbc:postgresql://<direct-host>/<db>?sslmode=require
ALTSTAY_DB_USER=altstay_app
ALTSTAY_DB_PASSWORD=<password>
```

`application.yaml` picks it up with
`spring.config.import: optional:file:./.env.properties`. The `optional:` prefix matters — without
it, a checkout with no credentials file refuses to start, and the offline test suite would break
for everyone.

Neon's copy-paste string also carries `channel_binding=require`, which is a **libpq** parameter the
Postgres JDBC driver does not accept. Keep `sslmode=require` and drop it.

**Use the connection host WITHOUT `-pooler` in it.** Neon offers a pooled endpoint that shares
backend connections between clients. The tenancy model binds the current tenant *to the connection*
with `SET LOCAL app.tenant_id`, so a shared connection is a route for one tenant's binding to reach
another tenant's query. The direct endpoint has no such behaviour. `sslmode=require` is likewise
not optional — this database is reached over the open internet.

**When you hand the machine back:** delete `backend/.env.properties`, and delete or reset the
database credentials in the provider console.

#### Confirm the role cannot bypass row-level security

Run these in the provider's **browser SQL editor** — no `psql` install needed, which matters on a
machine where you may not be able to install anything:

```sql
select current_user, usesuper from pg_user where usename = current_user;
select rolbypassrls from pg_roles where rolname = current_user;
select version();
```

`usesuper` and `rolbypassrls` must both be **false**, and the version must be **13 or higher**.
If either privilege check fails, the isolation tests will pass while isolation does nothing — the
worst possible outcome, because it reads as secure.

**On Neon this check has already failed once.** The default `neondb_owner` role returned
`rolbypassrls = true` on 2026-08-29. Connect as a dedicated `altstay_app` role instead, created
with **plain SQL** — roles created through the Neon console, CLI, or API are granted
`neon_superuser`, which carries BYPASSRLS:

```sql
create role altstay_app with login password '<strong>';
grant connect on database neondb to altstay_app;
grant usage, create on schema public to altstay_app;
```

Then re-run the two checks as `altstay_app` before trusting anything. Full write-up in §1.3 of
[phase-4-foundations.md](phase-4-foundations.md).

#### Expect a slow first query

Free tiers suspend an idle database and wake it on the next connection, so the first request after
a few minutes of quiet can take a second or two. **That is a cold start, not a timeout.** This
project has already lost a phase to a misread timeout (see `CLAUDE.md` on logging elapsed rather
than configured durations) — do not go tuning `model-read-timeout` because of it.

> **The offline suite must stay offline.** `mvnw clean verify` passes today with no API key and no
> network, and that must not regress. Database tests are opt-in behind `ALTSTAY_DB_TESTS=true`,
> mirroring `ALTSTAY_LIVE_TESTS`. If a plain `verify` starts needing the database, something has
> been wired wrong.

---

## 1. Terminal 1 — backend

```powershell
cd D:\Vikas\altstay\backend
```

```powershell
.\mvnw.cmd spring-boot:run
```

Ready when you see:

```
Tomcat started on port 8080 (http) with context path '/'
Started ApiApplication in ~1.3 seconds
```

Leave it running.

---

## 2. Terminal 2 — frontend

```powershell
cd D:\Vikas\altstay\frontend
```

```powershell
npm run dev
```

Ready when Next prints `Local: http://localhost:3000`.

---

## 3. Verify, bottom up

Check each layer before the one above it, so a failure tells you *where* it is.

> **PowerShell gotcha:** don't reach for `curl` here. Bare `curl` is an alias for
> `Invoke-WebRequest` and rejects the flags; `curl.exe` accepts them but Windows PowerShell 5.1
> eats the quotes out of an inline JSON `-d` argument, so any body containing a space arrives at
> the server split across several arguments. Both failure modes were live in this file until
> 2026-08-29. The commands below use `Invoke-RestMethod` with a hashtable body, which has neither
> problem. If you do want `curl.exe`, write the body to a file first and pass
> `--data-binary "@body.json"` — that is the only inline form that survives 5.1.

### 3.1 Backend is alive

```powershell
curl.exe -s http://localhost:8080/actuator/health
```

Expect `{"groups":["liveness","readiness"],"status":"UP"}`.

### 3.2 Backend answers from the knowledge base *(this one calls Gemini and costs a token)*

```powershell
$b = @{ propertyName='Sunset Surf Hostel'; knowledgeBase='Check-in is from 2 PM. Dorm bed is 650 rupees per night. No pets allowed.'; history=@(); message='what time can I check in?' } | ConvertTo-Json
Invoke-RestMethod -Uri http://localhost:8080/api/v1/chat -Method Post -ContentType 'application/json' -Body $b | ConvertTo-Json -Compress
```

Expect a reply mentioning **2 PM**, plus non-zero `usage` and `latencyMs`. If this fails, the
problem is the backend or the API key — stop here.

### 3.3 Backend escalates when the answer isn't in the knowledge base

```powershell
$b = @{ propertyName='Sunset Surf Hostel'; knowledgeBase='Check-in is from 2 PM. Dorm bed is 650 rupees per night.'; history=@(); message='do you have an airport shuttle?' } | ConvertTo-Json
Invoke-RestMethod -Uri http://localhost:8080/api/v1/chat -Method Post -ContentType 'application/json' -Body $b | ConvertTo-Json -Compress
```

Expect `"escalated": true` and no invented shuttle details.

### 3.4 The BFF proxy works

Same payload, but through Next on port 3000 — this proves the frontend's server route reaches
the backend:

```powershell
$b = @{ propertyName='Sunset Surf Hostel'; knowledgeBase='Check-in is from 2 PM. Dorm bed is 650 rupees per night. No pets allowed.'; history=@(); message='can I bring my dog?' } | ConvertTo-Json
Invoke-RestMethod -Uri http://localhost:3000/api/chat -Method Post -ContentType 'application/json' -Body $b | ConvertTo-Json -Compress
```

Expect the same response shape. A 502 here with the backend healthy means `BACKEND_URL` is wrong
or `.env.local` wasn't picked up (restart `npm run dev`).

### 3.5 Validation is rejected before it reaches Gemini

`Invoke-RestMethod` throws on a non-2xx, so this one needs the body read out of the exception:

```powershell
$b = @{ knowledgeBase='x'; history=@(); message='' } | ConvertTo-Json
try { Invoke-RestMethod -Uri http://localhost:3000/api/chat -Method Post -ContentType 'application/json' -Body $b } catch { $r = $_.Exception.Response; Write-Output "STATUS: $([int]$r.StatusCode)"; (New-Object System.IO.StreamReader($r.GetResponseStream())).ReadToEnd() }
```

Expect **400** and `"errors":{"message":"Message cannot be empty"}`. Nothing should appear in the
backend log — the BFF's zod schema stops it locally, which is the point.

> Check the `title` field, not just the status. If it says `Invalid JSON` rather than
> `Validation Failure`, PowerShell mangled the body and you have tested the JSON parser instead of
> the zod schema. That is exactly how the previous version of this step passed while proving
> nothing.

---

## 4. Verify in the browser — the demo that matters

Open <http://localhost:3000/concierge>.

**Phase 6 §0.1 moved the demo from `/` to `/concierge`** so that `/` can become the marketing
site in Phase 7; `/` now redirects to `/concierge`, so the old muscle-memory instruction — open
the bare origin — still lands on the demo. Prefer the direct `/concierge` URL here so a redirect
bug can never be mistaken for a demo bug.

1. **Live sync (the whole pitch).** In the right pane, find the check-in time in the knowledge
   base and change `2:00 PM` to `12:00 PM`. Don't reload. In the left pane ask
   *"what time is check-in?"* → the answer must say 12 PM.
2. **Escalation.** Ask something absent from the rules — *"do you rent motorbikes?"* → expect the
   escalation treatment on the bubble, not an invented answer.
3. **Metadata.** Expand the meta line under a reply → model, token count, latency.
4. **Presets.** Switch preset in the right pane → knowledge base and suggested questions swap.
5. **Backend down.** Stop Terminal 1 (`Ctrl+C`), send a message → expect *"The concierge is
   offline for a moment. Please retry."* with a working **Retry** button. Your question stays in
   the transcript as a sent bubble — the composer clears, and Retry re-sends that bubble. (The
   older wording here said the *typed message* is preserved in the composer; it isn't, and the
   actual behaviour is the better one.) Restart the backend, hit Retry → it should succeed.
6. **Mobile.** Narrow the window below `lg` → panes collapse into Guest Chat / Rules tabs.
7. **Keyboard.** `Enter` sends, `Shift+Enter` newlines, everything tab-reachable. Check this by
   hand — it is the one step in §4 that browser automation cannot stand in for.

### 4.1 Check the browser console

Open DevTools → Console **before** doing step 8 below.

8. **Refresh persistence.** Edit the knowledge base, wait a second, then press F5.

   The knowledge base should come back — **and there should be no hydration error in the
   console.** This regressed once already (localStorage read during the initial render); it only
   fails *after* you've edited something, so a fresh browser always looks fine. Keep checking it
   with a non-empty `localStorage`.

   You will see the default preset for a single frame before the saved value swaps in. That's the
   intended trade-off of loading storage after hydration, not a bug.

### 4.2 Capture dry run — do this before every beta session

[phase-3-validation.md](phase-3-validation.md) §5.1 asks you to prove capture works and then delete
the file. Capture is a **frontend** concern: `ALTSTAY_CAPTURE_DIR` is read by the BFF route, so it
must be set in the terminal that runs `npm run dev`, not the backend one.

```powershell
$env:ALTSTAY_CAPTURE_DIR = "D:/Vikas/altstay/.plans/phase-3-transcripts"
cd D:/Vikas/altstay/frontend; npm run dev
```

Hold a three-message conversation in the browser, edit the knowledge base once in the middle, then:

```powershell
Get-ChildItem $env:ALTSTAY_CAPTURE_DIR -Filter *.jsonl | Select-Object Name, Length, LastWriteTime
```

What a good file looks like — verified on this machine 2026-08-29:

| Line | Record | Meaning |
| --- | --- | --- |
| 1 | `kb` | Written on the **first** turn of the session |
| 2 | `turn` `status=200` | The exchange |
| 3 | `kb` (new `kbRef`) | Written again because the KB text changed — this is act 3's evidence |
| 4–5 | `turn` | Later exchanges, reusing the current `kbRef` |
| 6 | `turn` `status=502` `errorTitle="Model Unavailable"` `reply=null` | A failed call. **Failures are captured too** |

A `kb` record appears only when the knowledge base actually changes, so unchanged turns do not
re-emit it. That is correct behaviour, not a dropped record.

Then prove the off switch, because this is the privacy guarantee:

```powershell
Remove-Item Env:ALTSTAY_CAPTURE_DIR
```

Restart `npm run dev`, send another message, and confirm **no new file appears and no new line
appears in the Next.js console.** Verified: nothing is written and nothing is logged.

Delete the dry-run file before the session so it can't be mistaken for real data.

> **Known gap — read this before running two sessions on one day.** The browser does **not** send
> the `x-altstay-session` header that [phase-3-validation.md](phase-3-validation.md) §3.3 assumes.
> `frontend/src/lib/api.ts` posts to `/api/chat` with only `Content-Type`, so every session falls
> back to the route's default id and lands in **`local-<yyyy-mm-dd>.jsonl`**. Two partners on the
> same day append to the same file, silently interleaved.
>
> Until the header is wired up, the workaround is mechanical: give each session its **own**
> `ALTSTAY_CAPTURE_DIR`, restart `npm run dev` between them, and rename the file to
> `partner-a-<date>.jsonl` / `partner-b-<date>.jsonl` immediately afterwards. Restarting also
> resets the in-process KB-hash map in `capture.ts`, which guarantees partner B's file opens with
> its own `kb` record rather than inheriting partner A's.

---

## 5. Running the test suites

Backend (no network, no API key needed):

```powershell
cd D:\Vikas\altstay\backend; .\mvnw.cmd clean test
```

Frontend:

```powershell
cd D:\Vikas\altstay\frontend; npm run test
```

```powershell
cd D:\Vikas\altstay\frontend; npm run build
```

Verified 2026-08-29: `mvnw clean verify` is **BUILD SUCCESS** — 15 unit tests plus
`ModelTimeoutIT`, with `ChatLiveIT`'s 2 and `ConciergeEvalIT`'s 1 skipped without
`ALTSTAY_LIVE_TESTS=true`. Frontend `npm run test` is **12 passed**. Confirmed the backend suite
passes with `GOOGLE_API_KEY` unset — the model is never called.

---

## 6. Common failures

| Symptom | Cause |
| --- | --- |
| `invalid target release: 25` | `JAVA_HOME` still on JDK 17 — redo 0.1 and reopen the terminal |
| `npm : command not recognized` | Node not on PATH — redo 0.2 and reopen the terminal |
| Port 8080 in use | `Get-NetTCPConnection -LocalPort 8080` then stop the owning process |
| BFF returns 502, backend healthy | `BACKEND_URL` wrong, or `npm run dev` started before `.env.local` existed |
| BFF returns 502 after ~20s | The backend's own `model-read-timeout` fired. This is the expected path now — finding #6 is **closed** |
| BFF returns 504 | Backend exceeded the 25s BFF `AbortSignal` — the outer backstop. Since the backend bounds itself at 20s, seeing a 504 means the backend never answered at all, not that the model was slow |
| Composer greyed out, "Knowledge base exceeds the 20,000 character limit" | The pasted rules are over the cap. Chat is blocked until they are shortened — check a partner's rulebook length *before* the session |
| `curl: Invoke-WebRequest ... cannot find parameter` | You used `curl`, not `curl.exe` |
| Backend 400 on a long chat | History over 200 turns — expected |

---

## 7. The PMS core walkthrough

Phase 5's Definition of Done. Everything below runs against a tenant created by the provisioning
runner — **no hand-written SQL at any point**, which is the whole test: if a step here needs you to
open a SQL console, the API is missing something a property will need on day one.

The same sequence is asserted automatically by `BookingLifecycleIT` and `AllocationConstraintIT`;
this section is for watching it happen with your own eyes, which is a different kind of confidence.

### 7.1 Provision a tenant

The owner password comes from the environment and is never a command-line argument — an argument
would put the credential in the process list and in Spring's own `Environment`.

```powershell
$env:ALTSTAY_PROVISION_OWNER_PASSWORD = "<choose one>"
```

```powershell
cd D:\Vikas\altstay\backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=provision" "-Dspring-boot.run.arguments=--altstay.provision.tenant-slug=driftwood --altstay.provision.tenant-name=Driftwood Beach Hostel --altstay.provision.owner-email=owner@example.com --altstay.provision.property-name=Driftwood Goa --altstay.provision.timezone=Asia/Kolkata --altstay.provision.currency-code=INR"
```

`timezone` and `currency-code` are **required and have no defaults**. Omit either and the run fails
at binding rather than inventing one — §2's rule, because a defaulted timezone is a wrong answer
that looks like a right one.

It prints the tenant id, slug, owner email and property. It never prints the password.

### 7.2 Log in

```powershell
curl.exe -i -c cookies.txt -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"tenantSlug\":\"driftwood\",\"email\":\"owner@example.com\",\"password\":\"<the one you chose>\"}"
```

Expect 200, a `JSESSIONID` cookie, and `OWNER` in the body. Use `-b cookies.txt` on everything below.

### 7.3 One physical room, sold two ways

This is roadmap §5's crux and the reason the schema looks the way it does. Create **one** space with
six beds, then **two** room types over it — and map both to that same space.

```powershell
curl.exe -b cookies.txt -X POST http://localhost:8080/api/v1/properties/driftwood-goa/spaces -H "Content-Type: application/json" -d "{\"name\":\"Sea View Dorm\",\"floor\":\"1\",\"units\":[{\"label\":\"101-A\",\"unitKind\":\"BUNK_BOTTOM\"},{\"label\":\"101-B\",\"unitKind\":\"BUNK_TOP\"},{\"label\":\"101-C\",\"unitKind\":\"BUNK_BOTTOM\"},{\"label\":\"101-D\",\"unitKind\":\"BUNK_TOP\"},{\"label\":\"101-E\",\"unitKind\":\"BUNK_BOTTOM\"},{\"label\":\"101-F\",\"unitKind\":\"BUNK_TOP\"}]}"
```

A space with no units is refused: capacity is derived from the bed list and never stored, so a
space with nothing in it has no capacity and could not be sold anyway (§3.2).

```powershell
curl.exe -b cookies.txt -X POST http://localhost:8080/api/v1/properties/driftwood-goa/room-types -H "Content-Type: application/json" -d "{\"code\":\"DORM6MIX\",\"name\":\"6-bed mixed dorm\",\"saleMode\":\"PER_UNIT\",\"kind\":\"DORM\",\"maxOccupancy\":6,\"baseRateMinor\":60000}"
```

```powershell
curl.exe -b cookies.txt -X POST http://localhost:8080/api/v1/properties/driftwood-goa/room-types -H "Content-Type: application/json" -d "{\"code\":\"PRIV6\",\"name\":\"Whole room, private\",\"saleMode\":\"WHOLE\",\"kind\":\"DORM\",\"maxOccupancy\":6,\"baseRateMinor\":300000}"
```

Then map **both** room types to the **same** space (`POST /api/v1/room-types/{id}/spaces/{spaceId}`
for each). Two products, one set of six beds.

### 7.4 Availability before anything is sold

```powershell
curl.exe -b cookies.txt "http://localhost:8080/api/v1/properties/driftwood-goa/availability?from=2026-10-01&to=2026-10-08"
```

`DORM6MIX` shows `availableUnits: 6` every night; `PRIV6` shows `availableSpaces: 1` every night and
`bookableWholeSpaces: 1` for the range. Note the last one is an **intersection over the range**, not
a per-day count — a room free on Monday and occupied on Tuesday cannot be sold for Monday–Wednesday,
however good the per-day numbers look.

### 7.5 Sell one dorm bed, and watch the private room vanish

```powershell
curl.exe -b cookies.txt -X POST http://localhost:8080/api/v1/bookings -H "Content-Type: application/json" -d "{\"propertySlug\":\"driftwood-goa\",\"guest\":{\"fullName\":\"Test Guest\"},\"checkIn\":\"2026-10-01\",\"checkOut\":\"2026-10-05\",\"source\":\"DIRECT\",\"lines\":[{\"roomTypeId\":\"<DORM6MIX id>\",\"unitCount\":1}]}"
```

Check the totals: four nights at 60000 is `subtotalMinor: 240000`, **not** 60000. Pricing walks the
nights and consults the rate calendar per night; a booking priced per-booking rather than per-night
is the single most expensive kind of quiet bug in this system.

Re-run 7.4. `DORM6MIX` drops to 5 beds on 1–4 Oct, and `PRIV6` drops to **0 spaces** on those
nights, because one occupied bed means the room can no longer be sold whole. That coupling is one
GiST index, not application logic that has to remember both directions.

### 7.6 Check in, then check out early

```powershell
curl.exe -b cookies.txt -X POST http://localhost:8080/api/v1/bookings/<ALT-XXXXXX>/transitions -H "Content-Type: application/json" -d "{\"to\":\"CHECKED_IN\"}"
```

```powershell
curl.exe -b cookies.txt -X POST http://localhost:8080/api/v1/bookings/<ALT-XXXXXX>/transitions -H "Content-Type: application/json" -d "{\"to\":\"CHECKED_OUT\",\"reason\":\"left early\"}"
```

The allocations are shortened to end **today** in the same transaction, so tonight becomes sellable
immediately. §5.1 calls this the most commonly missed behaviour in a hostel PMS, and it is the
difference between a bed earning money tonight and sitting empty because the system still thinks
someone is in it.

A guest leaving on the day they arrived is handled too: there is no shorter range than zero nights,
and `check_out > check_in` forbids one, so the allocation is **released** instead of collapsed.

### 7.7 Cancel, and watch the bed come back

```powershell
curl.exe -b cookies.txt -X POST http://localhost:8080/api/v1/bookings/<ALT-YYYYYY>/transitions -H "Content-Type: application/json" -d "{\"to\":\"CANCELLED\",\"reason\":\"changed plans\"}"
```

The allocation rows get `released_at` and **stay**. Which bed that guest was in survives the
cancellation — a PMS is a system of record, and the partial exclusion constraint
(`where released_at is null`) is what lets both things be true at once.

### 7.8 The front desk's morning

```powershell
curl.exe -b cookies.txt "http://localhost:8080/api/v1/properties/driftwood-goa/front-desk"
```

Arrivals, departures and in-house for **today in the property's timezone**. Omit `?date=` and it
uses the property's own day boundary, never the server's.

### 7.9 Two guests, one bed

The thing the whole phase exists to prevent. Don't try to reproduce it by hand — races are not
reproducible by hand, which is the point. It is asserted by:

```powershell
$env:ALTSTAY_DB_TESTS = "true"; cd D:\Vikas\altstay\backend; .\mvnw.cmd failsafe:integration-test failsafe:verify "-Dit.test=BookingConcurrencyIT+AllocationConstraintIT"
```

`BookingConcurrencyIT` runs eight threads at one remaining bed and asserts exactly one 201 and seven
clean conflicts. `AllocationConstraintIT` case 11 then removes `allocation_no_overlap` **inside a
transaction it rolls back**, replays the identical inserts, and asserts that two guests now hold the
same bed — the incident the constraint prevents, observed rather than asserted. The schema is never
actually altered.

## 8. The staff console walkthrough

Phase 6's Definition of Done. Same story as §7 — one physical room sold two ways, the coupling
between them, a booking's full lifecycle — but through the console in a browser, by a person who
has never seen the schema. **No hand-written SQL and no `curl.exe` at any step.** If a step here
needs either, the console is missing something a front desk would need on day one.

Unlike §7, this doesn't hardcode October 2026 dates: run it whenever, and read "today" as *the
property's* today (Asia/Kolkata below), which is what every screen in the console itself uses —
never the browser's or the server's (phase-6-staff-console.md §7.2, and the reason `propertyToday`
exists at all).

Needs the frontend terminal from §2, and a provisioning run in place of §1's plain backend. §8.1's
`ApplicationRunner` prints the banner and returns, but the Spring Boot process it ran inside does
**not** exit — it keeps serving on :8080 exactly like §1's backend, so that terminal *is* your
backend for the rest of this walkthrough. Don't also start §1's plain backend: both bind :8080 and
the second one to start will fail. When you're done, stop it the same way you'd stop §1 (Ctrl+C).

### 8.1 Provision a tenant

A fresh tenant, distinct from §7's `driftwood` so the two walkthroughs never collide on the unique
tenant slug if both have been run against the same database.

```powershell
$env:ALTSTAY_PROVISION_OWNER_PASSWORD = "<choose one>"
```

```powershell
cd D:\Vikas\altstay\backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=provision" "-Dspring-boot.run.arguments=--altstay.provision.tenant-slug=riverbend --altstay.provision.tenant-name=Riverbend Rishikesh --altstay.provision.owner-email=owner@riverbend.example --altstay.provision.property-name=Riverbend Rishikesh --altstay.provision.timezone=Asia/Kolkata --altstay.provision.currency-code=INR"
```

It prints the tenant id, slug, owner email and property — never the password. `riverbend` and
`owner@riverbend.example` are the workspace and email you log in with next.

### 8.2 Log in

Open <http://localhost:3000> (or `/console` directly). The redirect from `/` lands on `/concierge`
(phase-6-staff-console.md §0.1) — that's the demo, not this. Navigate to
<http://localhost:3000/console/login> instead.

Fill in:

| Field | Value |
| --- | --- |
| Workspace | `riverbend` |
| Email | `owner@riverbend.example` |
| Password | *the one you chose in §8.1* |

Submit. You land on `/console` — Today — showing **"No bookings yet"** with a link to inventory
setup, not an empty arrivals list. That distinction (phase-6-staff-console.md §4.2, §10) is the
point: a brand-new tenant and a quiet Tuesday must not look identical.

### 8.3 One physical room, sold two ways

Click **Inventory** in the left nav (`/console/settings/inventory`).

**Room types** — under "Add room type", create two, one at a time:

| Field | First room type | Second room type |
| --- | --- | --- |
| Code | `DORM6MIX` | `PRIV6` |
| Name | `6-bed mixed dorm` | `Whole room, private` |
| Sale mode | Per unit (dorm beds) | Whole (private room) |
| Kind | Dorm | Dorm |
| Max occupancy | `6` | `6` |
| Base rate | `600` | `3000` |
| Active | checked | checked |

Both **Kind: Dorm** is deliberate, not a copy-paste mistake — a whole-dorm buyout (`WHOLE` +
`DORM`) is the example the settings screen itself uses to explain that sale mode is how capacity
is consumed and kind is what the guest thinks they're buying (§4.8).

**Spaces and units** — under "Add space": Name `101`, Floor `1`, then click **+ Add bed** five
times for six rows total and label them `101-A` through `101-F`, alternating Kind between
`BUNK_BOTTOM` and `BUNK_TOP`. Submit. The space list shows **"101 · floor 1 · 6 bed(s)"** — capacity
is derived from the beds you just added, never entered directly (it isn't editable anywhere in
this screen — phase-5 §3.2).

**Mapping** — find the `101` row. It starts as *"— nothing. This room cannot be sold"* (§4.8's
zero-mapping warning). Use **+ add** to map it to `6-bed mixed dorm`, then again to `Whole room,
private`. The row now reads **Sold as: [6-bed mixed dorm ×] [Whole room, private ×]** — one
physical room, two products.

### 8.4 Set a rate for a week

Click **Rates** in the nav. No rate plan exists yet, so only "Create a rate plan" shows. Create
one: Room type `DORM6MIX`, Code `STANDARD`, Name `Standard rate`, Default checked.

The screen now also shows a month grid for that plan, every day muted at **₹600.00** — the room
type's base rate, because no override exists yet (§4.9). Using "Set rate for a range": From
*today*, To *today + 6 days* — **inclusive of the end date**, unlike every booking range in this
console, which is half-open (§12.1's own note on this). Rate `650`. Submit. The same week now
shows **₹650.00**, no longer muted.

### 8.5 Open the calendar and see both products available

Click **Calendar**. Set From to *today*, leave Days at 14, click Update.

- `6-bed mixed dorm` shows **6 / 6** available every night, ₹650.00 for the week you just priced.
- `Whole room, private` shows **1 / 1** available spaces per night, and **1 bookable whole** in
  its row header — the range-wide count the wizard's `WHOLE` path actually uses, not the per-day
  number this grid renders (§4.3).

Read the legend beneath the grid — it's explaining exactly the coupling you're about to watch
happen in §8.7.

### 8.6 Book a dorm bed

Click today's cell in the `6-bed mixed dorm` row. It opens the wizard pre-filled with that room
type and date.

1. **Dates** — Check-in is already today; set Check-out to *today + 3* (a 3-night stay). Adults
   `1`, Children `0`. **Next: room.**
2. **Room** — `6-bed mixed dorm` is pre-selected, showing "6 available". Beds `1`. **Next: guest.**
3. **Guest** — switch to **New guest**: Full name `Priya Test Guest`, Email `priya@example.com`.
   **Next: review.**
4. **Review** — the quote loads with a per-night breakdown (₹650.00 × 3, since the whole stay
   falls inside the week you priced) and a total, computed by the backend, never summed in the
   browser (§4.6). **Confirm booking.**

You land on "Booking created." with a link to the new reference — click it. The booking detail
page shows **BOOKED**, and the only actions offered are **Checked in**, **Cancelled** and
**No-show** — never **Checked out**, because it isn't legal from `BOOKED` and the screen doesn't
offer illegal moves with a tooltip explaining why not (phase-6-staff-console.md §4.5, §7.3).

### 8.7 Watch the whole-room product disappear for those dates on the same calendar

Back to **Calendar**, same week. For the three nights you just booked:

- `6-bed mixed dorm` now shows **5 / 6**.
- `Whole room, private` now shows **0 / 1**, and **0 bookable whole** in its row header.

One dorm bed sold made the *entire private room* unsellable for those nights, in the same render,
from the same call — this is roadmap §5's crux and the reason the schema looks the way it does.
Nights outside the stay are untouched: `6-bed mixed dorm` is still 6/6 and `Whole room, private`
still 1/1 the day after checkout.

### 8.8 Check the guest in

Click **Today**. Because check-in is today, the booking appears under **Arrivals** with a
**Check in** button. Click it.

- If you're doing this at a moment that counts as "on time" (you are, since check-in is today),
  the list refreshes and the booking moves into **In house** with no extra note.
- `earlyCheckIn` only ever shows up if the booked check-in date is still in the future relative to
  the property's today — not reachable in this walkthrough, since the wizard itself refuses a
  check-in date before today (phase-6-staff-console.md §4.6's own validation). If you want to see
  the "early check-in noted, not blocked" behaviour it's there to surface (phase-6-staff-console.md
  §4.2), you'd need a booking made for a future date and checked in today — worth trying once
  you're comfortable with the rest of this flow.

### 8.9 Check out a night early, and see the freed night become bookable again

Open the booking (from **Bookings**, or the reference link on Today). It's now **CHECKED_IN**, and
the only actions offered are **Checked out** and **Cancelled** — `No-show` is gone, because it
isn't legal from here either.

Click **Checked out**. Leave the reason blank (it's optional) and **Confirm**.

Because you're checking out on the same calendar day as check-in, the backend releases tonight's
allocation outright rather than shortening a later date (`BookingService.transitionBooking`'s two
branches collapse into one when checkout happens same-day as check-in) — but the **observable
result is identical to a true mid-stay early checkout**: go back to **Calendar** and the nights
you didn't end up using are bookable again. `6-bed mixed dorm` and `Whole room, private` are back
to 6/6 and 1/1 for the remaining nights of what would have been a 3-night stay.

The more surgical case — checking out on a *later* calendar day than check-in, which only
*shortens* the stay instead of releasing it whole — needs the walkthrough to span real calendar
days, which a single sitting can't do. §7.6 demonstrates that exact branch directly against the
API with dates it controls; `BookingLifecycleIT` asserts it automatically either way.

### 8.10 Cancel a second booking, and see its bed return

Book a second stay the same way as §8.6 — same room type, a date range a few days later so it
doesn't overlap the first (e.g. *today + 5* through *today + 7*) — through to a confirmed booking.

Check **Calendar** for those dates: `6-bed mixed dorm` at 5/6, `Whole room, private` at 0/1, same
coupling as before.

Open the new booking's detail page. It's **BOOKED**, so the actions are **Checked in**,
**Cancelled**, **No-show**. Click **Cancelled**, give a reason (`"changed plans"` is fine), confirm.

The status history now shows the cancellation, and **Calendar** for those dates shows both room
types back to full availability — the allocation's `released_at` is set, but the row itself
**stays**, which is why the booking detail's "Beds on this booking" section can still show which
bed this guest held, struck through, rather than showing nothing at all (§4.5). A PMS is a system
of record: cancelling a booking must free the bed without erasing that it ever happened.

### 8.11 What this walkthrough doesn't cover

Two things §13's checklist calls out as still needing a real browser and a Gemini key with quota,
not a tenant: the `/concierge` demo re-walk (§4), and confirming the console itself **looks and
feels** right at 1280×800 and tablet width. Nothing in §8.1–§8.10 substitutes for either.
