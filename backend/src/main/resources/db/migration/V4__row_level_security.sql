-- Phase 4, Track A — isolation enforced by the database, not by application code
-- remembering to add a WHERE clause.
--
-- product-roadmap.md §4.1:
--   "Application-level filtering is one forgotten predicate away from showing
--    Hostel A's guest list to Hostel B. That's not a bug, it's an incident."
--
-- Three things below are load-bearing and each is a known way to get RLS wrong.
--
-- 1. FORCE ROW LEVEL SECURITY, not just ENABLE.
--    ENABLE exempts the table *owner* from its own policies. In a small
--    deployment the migration role and the runtime role are frequently the same
--    role, which means ENABLE alone leaves the table wide open while reading as
--    secured. FORCE closes that.
--
-- 2. The policy must fail CLOSED when no tenant is bound.
--    app_current_tenant() returns NULL when app.tenant_id is unset. `tenant_id =
--    NULL` is NULL, never true, so an unbound connection sees ZERO rows rather
--    than every row. A policy that fails open is indistinguishable from a
--    working one until the day it matters.
--
-- 3. WITH CHECK as well as USING.
--    USING filters what you can read; WITH CHECK constrains what you can write.
--    Without it, a bound tenant can INSERT rows carrying somebody else's
--    tenant_id — writing across the boundary it cannot read across.
--
-- Two operational conditions this migration cannot enforce, and which
-- TenantIsolationIT must assert instead:
--   * The runtime role must NOT be a superuser and must NOT hold BYPASSRLS.
--     Both bypass every policy here unconditionally.
--   * app.tenant_id must be set with SET LOCAL, inside the transaction, so it
--     cannot survive on a pooled connection into the next request.

create or replace function app_current_tenant() returns uuid
    language sql
    stable
as
$$
select nullif(current_setting('app.tenant_id', true), '')::uuid
$$;

comment on function app_current_tenant() is
    'Tenant bound to the current transaction. NULL when unset, so policies fail closed.';

-- `tenant` is the scope rather than a member of it, so its policy keys on id.
--
-- CONSEQUENCE, deliberate: with this policy a new tenant cannot be INSERTed over
-- an ordinary application connection, because WITH CHECK requires app.tenant_id
-- to already equal the id of the row being created. Tenant provisioning is
-- therefore an administrative operation and needs its own privileged path — a
-- separate role, or an explicit policy exemption — decided in Track B alongside
-- auth. That is the correct shape: provisioning a customer is not something a
-- request-scoped connection should be able to do. It is written down here so the
-- first person to hit it recognises it as a design decision and not a bug.
alter table tenant enable row level security;
alter table tenant force row level security;
create policy tenant_isolation on tenant
    for all
    using (id = app_current_tenant())
    with check (id = app_current_tenant());

alter table app_user enable row level security;
alter table app_user force row level security;
create policy tenant_isolation on app_user
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table user_role enable row level security;
alter table user_role force row level security;
create policy tenant_isolation on user_role
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table property enable row level security;
alter table property force row level security;
create policy tenant_isolation on property
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table knowledge_base enable row level security;
alter table knowledge_base force row level security;
create policy tenant_isolation on knowledge_base
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table knowledge_base_version enable row level security;
alter table knowledge_base_version force row level security;
create policy tenant_isolation on knowledge_base_version
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table conversation enable row level security;
alter table conversation force row level security;
create policy tenant_isolation on conversation
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table conversation_turn enable row level security;
alter table conversation_turn force row level security;
create policy tenant_isolation on conversation_turn
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

-- NOTE for future migrations: with FORCE enabled, a later migration that
-- backfills or seeds tenant-scoped rows will be filtered by these policies like
-- any other statement. Such a migration must bind app.tenant_id per tenant, or
-- explicitly and temporarily disable the policy for the duration of the
-- backfill. Silent zero-row backfills are the failure mode to watch for.
