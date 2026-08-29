-- Phase 4, Track A — the root of the isolation model.
--
-- Requires PostgreSQL 13+ for gen_random_uuid() without the pgcrypto extension.
--
-- Every table below except `tenant` itself carries tenant_id. `tenant` is the
-- scope rather than a member of it, and gets its own policy in V4.
--
-- The tenant id is resolved once per request from the authenticated principal
-- and is never read from a request body or header (product-roadmap.md §4.1).
-- A client-supplied tenant id is horizontal privilege escalation.

create table tenant (
    id          uuid primary key     default gen_random_uuid(),
    name        text        not null,
    slug        text        not null unique,
    created_at  timestamptz not null default now()
);

comment on table tenant is
    'One customer account. Root of the row-level-security model; see V4.';

create table app_user (
    id            uuid primary key     default gen_random_uuid(),
    tenant_id     uuid        not null references tenant (id) on delete cascade,
    email         text        not null,
    password_hash text        not null,
    full_name     text,
    is_active     boolean     not null default true,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

-- Case-insensitive uniqueness scoped to the tenant: the same person may hold an
-- account at two properties under different owners. lower() rather than citext
-- keeps this migration free of an extension dependency.
create unique index app_user_tenant_email_key
    on app_user (tenant_id, lower(email));

create index app_user_tenant_idx on app_user (tenant_id);

-- Roles per product-roadmap.md R1. A check constraint rather than a Postgres
-- enum: adding a value to an enum inside a transaction is restricted, and this
-- list will grow.
create table user_role (
    user_id   uuid not null references app_user (id) on delete cascade,
    tenant_id uuid not null references tenant (id) on delete cascade,
    role      text not null check (role in ('OWNER', 'MANAGER', 'FRONT_DESK')),
    primary key (user_id, role)
);

create index user_role_tenant_idx on user_role (tenant_id);
