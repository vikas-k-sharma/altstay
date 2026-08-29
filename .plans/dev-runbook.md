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

Open <http://localhost:3000>.

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
