# Deploying AltStay OS to Azure Container Apps

Target: **$0/month steady state**, with the $200 startup credit (expires **2026-11-13**) as a
buffer rather than the thing paying the bill. Everything below stays inside Azure Container Apps'
permanent monthly free grant at this traffic level, so nothing breaks when the credit runs out.

## What runs where

| Piece | Where | Ingress | Cost |
| --- | --- | --- | --- |
| `frontend/` (Next.js 16) | ACA app `altstay-web` | **External** — the public site | Free grant |
| `backend/` (Spring Boot 4) | ACA app `altstay-api` | **Internal** — no public route at all | Free grant |
| Database | Neon (already live, ap-southeast-1) | — | Free tier |
| Container images | ghcr.io, private | — | Free |
| Gemini | Google AI Studio | — | **See §8 — this is the real blocker** |

### Why the backend has no public URL

`SecurityConfig.java` disables CSRF on `/api/v1/**`, and phase-4 §3.4 records why: *the browser
never calls Spring directly, only the Next.js BFF does, server-to-server.* CLAUDE.md calls that
load-bearing — "if a browser ever gains a direct route to Spring, that decision is void."

Giving `altstay-api` **internal** ingress makes that invariant a property of the network rather
than a convention. Both apps sit in one Container Apps Environment; `altstay-web` can reach the
API on its internal FQDN, and nothing outside the environment can reach it at all. No code
change, no shared-secret header, and the invariant cannot be violated by a later edit to the
security config.

Region is **southeastasia** throughout, to sit next to Neon's `ap-southeast-1`. Every query the
API makes crosses that gap, so putting the app in another region taxes every page load.

---

## 1. Prerequisites

You need no local tooling. This machine has no `docker` and no `az`, and does not need either:
images are built by GitHub Actions, and every Azure command below runs in
**[Azure Cloud Shell](https://portal.azure.com)** (the `>_` icon in the portal top bar — pick
**Bash**), which has `az` pre-installed and already signed in.

What you do need:

- The Azure subscription carrying the startup credit.
- The GitHub repo (`vikas-k-sharma/altstay`) — push access.
- Your Neon connection details, currently in gitignored `backend/.env.properties`.
- A `GOOGLE_API_KEY`.

---

## 2. Get the images built

Commit and push to `main`. `.github/workflows/deploy.yml` runs the offline test suites, then
builds and pushes two private packages to ghcr.io:

- `ghcr.io/vikas-k-sharma/altstay-api`
- `ghcr.io/vikas-k-sharma/altstay-web`

The `deploy` job skips itself until the `AZURE_RESOURCE_GROUP` repository variable exists, so this
first push builds images and stops there — which is what you want before any Azure resource is
created.

Confirm both packages appear under your GitHub profile → **Packages** before continuing.

---

## 3. A token for Azure to pull with

The packages are private, so ACA needs credentials. Create a **classic** personal access token at
<https://github.com/settings/tokens> with the single scope **`read:packages`**, and nothing else.

> Keep this token out of the repo. It goes straight into an ACA secret in §5 and is never written
> to a file here.

---

## 4. Create the environment

In Cloud Shell (Bash). Fill in the values at the top, then paste the block.

```bash
export RG=altstay-rg
export LOC=southeastasia
export ENVNAME=altstay-env
export GH_USER=vikas-k-sharma

az account show --query "{name:name, id:id}" -o table

az provider register --namespace Microsoft.App --wait
az provider register --namespace Microsoft.OperationalInsights --wait

az group create --name "$RG" --location "$LOC"

az containerapp env create --name "$ENVNAME" --resource-group "$RG" --location "$LOC"
```

The environment creates a Log Analytics workspace for you. At this traffic it stays inside Azure
Monitor's free ingestion allowance.

---

## 5. The API — internal ingress

Set the secret values, then create the app. `ALTSTAY_DB_URL` **must be Neon's direct host, not the
`-pooler` one** — tenancy binds the tenant to the connection with `SET LOCAL`, and a
transaction-mode pooler multiplexes sessions across backends (CLAUDE.md). Keep `sslmode=require`
and drop `channel_binding=require`, which is libpq-only and the JDBC driver rejects.

```bash
export GHCR_PAT=PASTE_THE_READ_PACKAGES_TOKEN
export DB_URL=PASTE_JDBC_URL_WITH_DIRECT_HOST
export DB_USER=altstay_app
export DB_PASSWORD=PASTE_ALTSTAY_APP_PASSWORD
export GEMINI_KEY=PASTE_GOOGLE_API_KEY
```

```bash
az containerapp create --name altstay-api --resource-group "$RG" --environment "$ENVNAME" --image "ghcr.io/$GH_USER/altstay-api:latest" --registry-server ghcr.io --registry-username "$GH_USER" --registry-password "$GHCR_PAT" --ingress internal --target-port 8080 --transport auto --cpu 0.5 --memory 1.0Gi --min-replicas 0 --max-replicas 2 --secrets db-url="$DB_URL" db-user="$DB_USER" db-password="$DB_PASSWORD" google-api-key="$GEMINI_KEY" --env-vars ALTSTAY_DB_URL=secretref:db-url ALTSTAY_DB_USER=secretref:db-user ALTSTAY_DB_PASSWORD=secretref:db-password GOOGLE_API_KEY=secretref:google-api-key ALTSTAY_MODEL=gemini-2.5-flash-lite ALTSTAY_COOKIE_SECURE=true
```

`0.5 vCPU / 1.0Gi` is the smallest ACA combination a Spring Boot 4 + Hibernate + Spring AI JVM
starts reliably in. The Dockerfile sets `-XX:MaxRAMPercentage=75`, so the heap follows this limit
rather than a number hardcoded in the image.

Flyway runs V1–V11 on first boot. They are already applied to Neon, so it should find the schema
current and do nothing — but watch the log for it rather than assuming:

```bash
az containerapp logs show --name altstay-api --resource-group "$RG" --tail 100
```

Now capture the internal FQDN — `altstay-web` needs it:

```bash
export API_FQDN=$(az containerapp show --name altstay-api --resource-group "$RG" --query properties.configuration.ingress.fqdn -o tsv)
echo "$API_FQDN"
```

It looks like `altstay-api.internal.<id>.southeastasia.azurecontainerapps.io`, and is resolvable
only from inside the environment — that is the point.

---

## 6. The web app — external ingress

```bash
az containerapp create --name altstay-web --resource-group "$RG" --environment "$ENVNAME" --image "ghcr.io/$GH_USER/altstay-web:latest" --registry-server ghcr.io --registry-username "$GH_USER" --registry-password "$GHCR_PAT" --ingress external --target-port 3000 --transport auto --cpu 0.25 --memory 0.5Gi --min-replicas 1 --max-replicas 3 --env-vars BACKEND_URL="https://$API_FQDN"
```

```bash
az containerapp show --name altstay-web --resource-group "$RG" --query properties.configuration.ingress.fqdn -o tsv
```

That last command prints your public URL.

**`--min-replicas 1` on the web app is deliberate.** Scale-to-zero would put a container cold
start in front of every first visitor. A warm replica costs single-digit dollars a month at ACA's
reduced idle rate — a good use of a credit that expires anyway. Flip it to `0` before 2026-11-13
to return to $0. The API stays at `0` because only a logged-in console user waits on it, and §9
covers pinning it warm for the beta sessions.

> **If the web app logs a TLS certificate error against the internal FQDN**, switch `BACKEND_URL`
> to `http://$API_FQDN`. Traffic stays inside the environment either way, and the BFF parses
> Spring's `Set-Cookie` by hand (`frontend/src/app/api/console/login/route.ts:12`), so the
> cookie's `Secure` attribute has no bearing on whether login works.

---

## 7. Wire the site URL and continuous deploy

### 7a. Rebuild with the real hostname

`SITE_URL` is baked in at build time — `metadataBase`, `robots.txt` and `sitemap.xml` are all
evaluated during `next build`. Left unset, the build defaults to `https://altstay.in` and declares
itself indexable, which on an `*.azurecontainerapps.io` host means publishing canonicals for a
domain it is not served from.

Set the repository variable to your actual public URL. `robots.ts` then serves `Disallow: /` for
everything, keeping the staging host out of search:

```bash
gh variable set ALTSTAY_SITE_URL --body https://REPLACE_WITH_YOUR_WEB_FQDN
```

When you later point `altstay.in` at the app, change this variable to `https://altstay.in` and the
next build flips indexing back on by itself.

### 7b. Let CI roll the revisions

Create a service principal scoped to this resource group only:

```bash
az ad sp create-for-rbac --name altstay-deploy --role contributor --scopes "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$RG" --json-auth
```

Copy the whole JSON object it prints, then:

```bash
gh secret set AZURE_CREDENTIALS
```

```bash
gh variable set AZURE_RESOURCE_GROUP --body altstay-rg
```

From the next push to `main`: tests run, images build, and both apps roll to the new commit SHA —
API first, so a schema migration lands before the web revision that depends on it.

---

## 8. The thing that will actually break your demo

Not deployment. **The Gemini key.**

CLAUDE.md records this as the most schedule-critical item in the repo: the free tier's binding
limit is **20 requests per day per project per model**, verified 2026-08-29 against the running
backend. Once spent, every question renders *"The concierge is offline for a moment."* for the
rest of the day, and `retryOptions(attempts(1))` means a 429 is never retried.

Switching models does not help — it was tried. Each model gets its own bucket of 20, not a bigger
one. **A single beta session asks more than 20 questions.** Enable billing on the Google Cloud
project behind the key before the October sessions, or this deploy is a website with a broken
concierge on it.

---

## 9. Before the October beta sessions

```bash
az containerapp update --name altstay-api --resource-group altstay-rg --min-replicas 1
```

```bash
az containerapp update --name altstay-api --resource-group altstay-rg --min-replicas 0
```

The first pins the API warm so there is no 10–15s Spring cold start in front of a watching owner;
the second returns it to zero afterwards.

Also worth doing first: seed a tenant and an `OWNER` user so the console has something to log into,
and check `/actuator/health` from a shell *inside* the environment
(`az containerapp exec --name altstay-web --resource-group altstay-rg --command sh`, then `curl`
the internal FQDN) rather than from your laptop — from outside it is unreachable by design, and
that unreachability is not a fault.

---

## 10. Cost, honestly

Azure Container Apps includes a permanent monthly free grant per subscription — on the order of
180,000 vCPU-seconds, 360,000 GiB-seconds and 2M requests. Two scale-to-zero apps at pre-launch
traffic do not approach it.

The one deliberate exception is `--min-replicas 1` on the web app, which bills at ACA's reduced
idle rate. Expect roughly **$5–8/month**, comfortably inside the $200 credit for the fourteen
months it has left. Confirm against the
[pricing calculator](https://azure.microsoft.com/pricing/calculator/) rather than these figures —
rates change, and these are estimates, not quotes.

Set a budget alert so nothing surprises you: **Cost Management → Budgets** in the portal, scoped to
the `altstay-rg` resource group, with an alert at a threshold you would want to hear about.

**Before 2026-11-13**, when the credit expires: set the web app's `--min-replicas 0`. Steady-state
cost returns to $0 and nothing else needs to change.
