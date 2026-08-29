-- Phase 4, Track B — Tenant Directory for tenant-scoped authentication.
--
-- V1 keys users on unique(tenant_id, lower(email)), and app_user has FORCE RLS,
-- so an unbound connection sees zero users. Authentication cannot look up a user
-- before a tenant is bound, and cannot bind a tenant before it knows the user.
--
-- tenant_directory breaks this cycle by providing an unprotected lookup from
-- slug to tenant_id with NO personal data (no email, no name, no password hash).
-- Maintained automatically from `tenant` via trigger so it cannot drift.

create table tenant_directory (
    slug      text primary key,
    tenant_id uuid not null references tenant (id) on delete cascade
);

comment on table tenant_directory is
    'Public lookup table mapping tenant slug to tenant_id. No RLS, no PII. Maintained from tenant via trigger.';

create index tenant_directory_tenant_id_idx on tenant_directory (tenant_id);

create or replace function sync_tenant_directory()
returns trigger as $$
begin
    if (tg_op = 'INSERT') then
        insert into tenant_directory (slug, tenant_id)
        values (new.slug, new.id)
        on conflict (slug) do update set tenant_id = excluded.tenant_id;
        return new;
    elsif (tg_op = 'UPDATE') then
        if (old.slug <> new.slug) then
            delete from tenant_directory where slug = old.slug;
        end if;
        insert into tenant_directory (slug, tenant_id)
        values (new.slug, new.id)
        on conflict (slug) do update set tenant_id = excluded.tenant_id;
        return new;
    elsif (tg_op = 'DELETE') then
        delete from tenant_directory where slug = old.slug;
        return old;
    end if;
    return null;
end;
$$ language plpgsql;

create trigger trg_sync_tenant_directory
    after insert or update or delete on tenant
    for each row
    execute function sync_tenant_directory();

-- Backfill any existing tenants.
alter table tenant disable row level security;

insert into tenant_directory (slug, tenant_id)
select slug, id from tenant
on conflict (slug) do update set tenant_id = excluded.tenant_id;

alter table tenant enable row level security;
alter table tenant force row level security;
