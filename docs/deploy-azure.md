# Deploying AltStay OS to Azure Container Apps

The complete procedure, start to finish. Follow it top to bottom to rebuild the entire deployment
from nothing — after a teardown, on a new Azure subscription, or on a new machine.

First deployed **2026-09-02**. Live at
<https://altstay-web.yellowriver-ae1bc796.southeastasia.azurecontainerapps.io>

---

## Which terminal am I in?

Every command in this file belongs to one of two places. This is the single most common source of
confusion, so check the first word:

| Command starts with… | Run it in… |
| --- | --- |
| `az` | **Azure Cloud Shell** — the terminal inside <https://portal.azure.com> (the `>_` icon in the top bar, choose **Bash**) |
| `git` | **PowerShell on your own PC**, in the repo root |
| `gh` | Your PC — but every `gh` step below also has a click-through alternative on the GitHub website |

Cloud Shell already has `az` installed and already knows who you are. You never need to install
the Azure CLI or Docker locally. This machine has neither.

---

## What gets deployed

| Piece | Where | Ingress | Cost |
| --- | --- | --- | --- |
| `frontend/` (Next.js 16) | ACA app `altstay-web` | **External** — the public site | ~$4–6/mo, or $0 (§9) |
| `backend/` (Spring Boot 4) | ACA app `altstay-api` | **Internal** — no public route at all | ~$0 |
| Database | Neon, ap-southeast-1 | — | Free tier |
| Container images | ghcr.io, private | — | Free |
| Gemini | Google AI Studio | — | **See §10** |

Fixed names used throughout: resource group `altstay-rg`, environment `altstay-env`, region
`southeastasia` (chosen to sit beside Neon's `ap-southeast-1` — every query the API makes crosses
that gap).

### Why the backend has no public URL

`SecurityConfig.java` disables CSRF on `/api/v1/**`, and phase-4 §3.4 records why: *the browser
never calls Spring directly, only the Next.js BFF does, server-to-server.* CLAUDE.md calls that
load-bearing — "if a browser ever gains a direct route to Spring, that decision is void."

Giving `altstay-api` **internal** ingress makes that a property of the network rather than a
convention. Both apps sit in one Container Apps Environment; `altstay-web` reaches the API on its
internal FQDN, and nothing outside the environment can reach it at all. No code change, no
shared-secret header, and no later edit to the security config can accidentally void it.

> **If you tear down and rebuild, your public URL will change.** The `yellowriver-ae1bc796` part
> is generated fresh with each new environment. Expect a new hostname and update
> `ALTSTAY_SITE_URL` (§7) to match.

---

## 1. Collect five values first

Put these in a scratch file before you start. Hunting for them mid-procedure is where people get
stuck.

| # | Value | Where to find it |
| --- | --- | --- |
| 1 | `ALTSTAY_DB_URL` | `backend/.env.properties` (gitignored) |
| 2 | `ALTSTAY_DB_USER` | same file — should be `altstay_app`, **never** `neondb_owner` |
| 3 | `ALTSTAY_DB_PASSWORD` | same file |
| 4 | `GOOGLE_API_KEY` | your machine's environment — `echo $env:GOOGLE_API_KEY` |
| 5 | GitHub token | create it, see below |

The DB URL must use Neon's **direct host, not the `-pooler` one** — tenancy binds the tenant to
the connection with `SET LOCAL`, and a transaction-mode pooler multiplexes sessions across
backends. Keep `sslmode=require`; drop `channel_binding=require`, which is libpq-only and the JDBC
driver rejects. Verified correct as of 2026-09-02.

**The GitHub token** lets Azure pull your private images. At
<https://github.com/settings/tokens> → **Generate new token (classic)** → name `azure-pull`,
expiry **No expiration**, and tick **exactly one** box: **`read:packages`**.

---

## 2. Push the code — PC

```bash
git push
```

`.github/workflows/deploy.yml` runs on every push to `main`.

---

## 3. Let GitHub build the images

Watch <https://github.com/vikas-k-sharma/altstay/actions>.

The workflow runs the offline test suites (~4 min), then builds and pushes two private packages
(~6 min):

- `ghcr.io/vikas-k-sharma/altstay-api`
- `ghcr.io/vikas-k-sharma/altstay-web`

**Wait for green**, then confirm both appear at
<https://github.com/vikas-k-sharma?tab=packages>.

The `deploy` job skips itself until the `AZURE_RESOURCE_GROUP` repository variable exists (§7), so
on a fresh rebuild this run stops after building — which is what you want before any Azure resource
exists.

---

## 4. Azure one-time setup — Cloud Shell

Confirm you are on the right subscription:

```bash
az account show --query "{subscription:name, id:id}" -o table
```

If it names the wrong one: `az account set --subscription "NAME"`.

Install the extension and register the providers (~3 min, safe to re-run):

```bash
az extension add --name containerapp --upgrade --only-show-errors && az provider register --namespace Microsoft.App --wait && az provider register --namespace Microsoft.OperationalInsights --wait && echo "PROVIDERS READY"
```

---

## 5. Create the resource group and environment — Cloud Shell

```bash
export RG=altstay-rg && export LOC=southeastasia && export ENVNAME=altstay-env && export GH_USER=vikas-k-sharma && echo "vars set"
```

```bash
az group create --name "$RG" --location "$LOC" -o table
```

```bash
az containerapp env create --name "$ENVNAME" --resource-group "$RG" --location "$LOC" -o table
```

The environment creates a Log Analytics workspace automatically. At this traffic it stays inside
Azure Monitor's free ingestion allowance.

---

## 6. Create the two apps — Cloud Shell

### 6a. Paste the five secrets

**Use single quotes exactly as shown** — the DB URL and password contain characters the shell
would otherwise mangle.

```bash
export GHCR_PAT='PASTE_TOKEN_5_HERE'
```

```bash
export DB_URL='PASTE_VALUE_1_HERE'
```

```bash
export DB_USER='PASTE_VALUE_2_HERE'
```

```bash
export DB_PASSWORD='PASTE_VALUE_3_HERE'
```

```bash
export GEMINI_KEY='PASTE_VALUE_4_HERE'
```

Every line must say `OK`:

```bash
for v in GHCR_PAT DB_URL DB_USER DB_PASSWORD GEMINI_KEY; do if [ -n "${!v}" ]; then echo "$v OK"; else echo "$v MISSING"; fi; done
```

### 6b. The backend — internal ingress

```bash
az containerapp create --name altstay-api --resource-group "$RG" --environment "$ENVNAME" --image "ghcr.io/$GH_USER/altstay-api:latest" --registry-server ghcr.io --registry-username "$GH_USER" --registry-password "$GHCR_PAT" --ingress internal --target-port 8080 --transport auto --cpu 0.5 --memory 1.0Gi --min-replicas 0 --max-replicas 2 --secrets db-url="$DB_URL" db-user="$DB_USER" db-password="$DB_PASSWORD" google-api-key="$GEMINI_KEY" --env-vars ALTSTAY_DB_URL=secretref:db-url ALTSTAY_DB_USER=secretref:db-user ALTSTAY_DB_PASSWORD=secretref:db-password GOOGLE_API_KEY=secretref:google-api-key ALTSTAY_MODEL=gemini-2.5-flash-lite ALTSTAY_COOKIE_SECURE=true -o table
```

`0.5 vCPU / 1.0Gi` is the smallest ACA combination this JVM starts reliably in. The Dockerfile
sets `-XX:MaxRAMPercentage=75`, so the heap follows this limit rather than a number hardcoded in
the image.

Flyway runs V1–V11 on boot. Against an existing Neon database it finds the schema current and does
nothing — but watch for `Started AltstayApiApplication` rather than assuming:

```bash
az containerapp logs show --name altstay-api --resource-group "$RG" --tail 60
```

### 6c. Capture the backend's private address

```bash
export API_FQDN=$(az containerapp show --name altstay-api --resource-group "$RG" --query properties.configuration.ingress.fqdn -o tsv) && echo "$API_FQDN"
```

Must print something containing `.internal.` and ending `.azurecontainerapps.io`. If it's blank,
stop — 6b didn't finish.

### 6d. The frontend — external ingress

```bash
az containerapp create --name altstay-web --resource-group "$RG" --environment "$ENVNAME" --image "ghcr.io/$GH_USER/altstay-web:latest" --registry-server ghcr.io --registry-username "$GH_USER" --registry-password "$GHCR_PAT" --ingress external --target-port 3000 --transport auto --cpu 0.25 --memory 0.5Gi --min-replicas 1 --max-replicas 3 --env-vars BACKEND_URL="https://$API_FQDN" -o table
```

### 6e. Your public URL

```bash
echo "https://$(az containerapp show --name altstay-web --resource-group "$RG" --query properties.configuration.ingress.fqdn -o tsv)"
```

---

## 7. Wire up automatic deployment

### 7a. Create a deploy identity — Cloud Shell

```bash
az ad sp create-for-rbac --name altstay-deploy --role contributor --scopes "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$RG" --json-auth
```

Copy the entire JSON block it prints, from the first `{` to the last `}`.

> On a rebuild, an `altstay-deploy` identity may already exist from last time. Remove the stale one
> first: `az ad sp delete --id $(az ad sp list --display-name altstay-deploy --query "[0].id" -o tsv)`

### 7b. Add one secret and two variables — GitHub website

**Secret** — <https://github.com/vikas-k-sharma/altstay/settings/secrets/actions> →
**New repository secret**:

| Name | Value |
| --- | --- |
| `AZURE_CREDENTIALS` | the JSON from 7a |

**Variables** — the **Variables** tab on that same page →
**New repository variable**, twice:

| Name | Value |
| --- | --- |
| `AZURE_RESOURCE_GROUP` | `altstay-rg` |
| `ALTSTAY_SITE_URL` | your URL from 6e, **no trailing slash** |

`ALTSTAY_SITE_URL` is baked in at build time — `metadataBase`, `robots.txt` and `sitemap.xml` are
all evaluated during `next build`. Left unset, the build defaults to `https://altstay.in` and
declares itself indexable, which on an `*.azurecontainerapps.io` host means publishing canonicals
for a domain it is not served from. Set to anything other than `https://altstay.in`, `robots.ts`
fails closed and serves `Disallow: /`. When you point `altstay.in` at the app, change this variable
and the next build flips indexing back on by itself.

No trailing slash: the sitemap appends its own, and you would get `//` in every URL.

### 7c. Trigger the first automated deploy — PC

```bash
git commit --allow-empty -m "Trigger first automated deploy"
```

```bash
git push
```

This run goes one job further than before: a **deploy** job that rolls both Azure apps — API first,
so a schema migration lands before the web revision depending on it. From here, `git push` is the
entire deployment process.

---

## 8. Verify it actually works

The marketing pages are **static**. They render perfectly even if the frontend cannot reach the
backend at all, so "the site loads" is not proof the wiring is correct.

Open `/console/login` on your public URL and attempt a login with a deliberately wrong address like
`nope@example.com`:

- **A clean "invalid credentials" message** → backend connected. Fully deployed.
- **A crash, a 500, or a spinner that never resolves** → the frontend cannot reach Spring. See §11.

---

## 9. Cost, and the two commands that control it

At near-zero traffic, exactly one thing costs money: `altstay-web` runs `--min-replicas 1`, keeping
one container alive so visitors never wait for a cold start.

| Component | Config | Monthly cost |
| --- | --- | --- |
| `altstay-web` | 0.25 vCPU / 0.5 GiB, **always on** | **~$4–6** |
| `altstay-api` | 0.5 vCPU / 1 GiB, scales to zero | ~$0 — a few cents per hour it is awake |
| Container Apps Environment | Consumption | $0 |
| Log Analytics | container logs | $0 — under the free ingestion allowance |
| Neon Postgres | free tier | $0 |
| ghcr.io images | private packages | $0 |
| Bandwidth | first 100 GB/mo free | $0 |

The web app burns 648,000 vCPU-seconds and 1,296,000 GiB-seconds a month just existing. Azure's
free grant (180,000 vCPU-s + 360,000 GiB-s per subscription per month) covers part of it; the rest
bills at the *idle* rate, roughly an eighth of the active rate. The range is $4–6 rather than one
number because Azure's docs are ambiguous about whether the free grant offsets idle time or only
active time — both readings land in that band, and regional rates vary.

### The zero-cost setting

```bash
az containerapp update --name altstay-web --resource-group altstay-rg --min-replicas 0
```

Cost drops to **$0/month**. After ~5 minutes with no visitors the container shuts down, and the
next person waits **2–4 seconds** for it to start. Correct choice for a dormant testing deployment.

### The always-warm setting

```bash
az containerapp update --name altstay-web --resource-group altstay-rg --min-replicas 1
```

Costs **~$4–6/month**. No cold start, ever. Correct choice while anyone is actually being shown the
product.

The same two commands work on `altstay-api` — swap the name. Pin it warm before a live demo so
there is no 10–15s Spring cold start in front of a watching owner:

```bash
az containerapp update --name altstay-api --resource-group altstay-rg --min-replicas 1
```

```bash
az containerapp update --name altstay-api --resource-group altstay-rg --min-replicas 0
```

### When the startup credit expires — 2026-11-13

A credit-only subscription does not quietly start charging you. It gets **disabled**, and resources
are stopped and eventually deleted. So on that date, one of two things happens:

- **No payment method attached** → the site goes dark. No bill, no site.
- **Card attached** → it keeps running and you start paying the figures above.

Decide on purpose: **portal.azure.com → Subscriptions → your subscription → Payment methods**.

Until then, leave it warm. Unspent credit is lost credit.

### See the real number, not this estimate

**Cost Management → Cost analysis**, scoped to the `altstay-rg` resource group, view set to
**Daily**. Multiply a typical day by 30. While credits are active it shows spend drawn against the
credit rather than billed, but the daily figure is the same one you would pay later.

Set a spending alert so nothing can surprise you: **Cost Management → Budgets → Add**, scope
`altstay-rg`, amount $10, alert at 80%. A crash loop or traffic spike then reaches you at $8
instead of at the end of the month.

---

## 10. The thing that will break the demo before hosting does

**The Gemini key.** CLAUDE.md records this as the most schedule-critical item in the repo: the free
tier's binding limit is **20 requests per day per project per model**, verified 2026-08-29 against
the running backend. Once spent, every question renders *"The concierge is offline for a moment."*
for the rest of the day, and `retryOptions(attempts(1))` means a 429 is never retried.

Switching models does not help — it was tried. Each model gets its own bucket of 20, not a bigger
one. **A single beta session asks more than 20 questions.** Enable billing on the Google Cloud
project behind the key before the October sessions, or this deployment is a working website with a
broken concierge on it.

---

## 11. When something breaks

**The web app errors, or login hangs.** Check the logs for a TLS complaint about the internal
address:

```bash
az containerapp logs show --name altstay-web --resource-group altstay-rg --tail 60
```

If you see a certificate error, switch to plain HTTP — traffic never leaves Azure's private
network either way, and the BFF parses Spring's `Set-Cookie` by hand
(`frontend/src/app/api/console/login/route.ts:12`), so the cookie's `Secure` attribute has no
bearing on whether login works:

```bash
az containerapp update --name altstay-web --resource-group altstay-rg --set-env-vars BACKEND_URL="http://$API_FQDN"
```

**`UNAUTHORIZED` or image pull failure in 6b/6d.** The token from §1 is wrong, expired, or lacks
`read:packages`. Fastest fix: make both packages public at
<https://github.com/vikas-k-sharma?tab=packages> → each package → **Package settings** → **Change
visibility** → Public. Then re-run the create command without the three `--registry-*` flags.

**The backend will not start.** Almost always the database. Read the log:

```bash
az containerapp logs show --name altstay-api --resource-group altstay-rg --tail 100
```

- *"Failed to determine a suitable driver class"* → `ALTSTAY_DB_URL` is empty or malformed.
- Authentication failure → wrong user or password, or you used `neondb_owner`, which has
  `rolbypassrls = true` and must never be the app's connection role.
- A slow first connection is a Neon cold start, not a fault. Free-tier compute suspends when idle.

**Start completely over.** Deletes every Azure resource. Costs nothing, takes ~2 minutes. Your
public URL will be different afterwards:

```bash
az group delete --name altstay-rg --yes --no-wait
```

---

## 12. Quick reference

```bash
az containerapp logs show --name altstay-api --resource-group altstay-rg --tail 60
```

```bash
az containerapp logs show --name altstay-web --resource-group altstay-rg --tail 60
```

```bash
az containerapp revision list --name altstay-web --resource-group altstay-rg -o table
```

```bash
az containerapp show --name altstay-web --resource-group altstay-rg --query properties.configuration.ingress.fqdn -o tsv
```

Redeploy after a code change: `git push`. Nothing else.
