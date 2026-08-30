-- V7__inventory.sql
-- PMS Core: Room types, spaces, units, and hybrid room_type_space mapping.

create table room_type (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid not null references tenant (id) on delete cascade,
    property_id     uuid not null references property (id) on delete cascade,
    code            text not null,
    name            text not null,
    sale_mode       text not null check (sale_mode in ('PER_UNIT', 'WHOLE')),
    kind            text not null check (kind in ('DORM', 'PRIVATE')),
    max_occupancy   integer not null check (max_occupancy > 0),
    base_rate_minor bigint not null check (base_rate_minor >= 0),
    description     text,
    is_active       boolean not null default true,
    created_at      timestamptz not null default now(),
    unique (tenant_id, property_id, code)
);

create table space (
    id          uuid primary key default gen_random_uuid(),
    tenant_id   uuid not null references tenant (id) on delete cascade,
    property_id uuid not null references property (id) on delete cascade,
    name        text not null,
    floor       text,
    is_active   boolean not null default true,
    created_at  timestamptz not null default now(),
    unique (tenant_id, property_id, name)
);

create table unit (
    id         uuid primary key default gen_random_uuid(),
    tenant_id  uuid not null references tenant (id) on delete cascade,
    space_id   uuid not null references space (id) on delete cascade,
    label      text not null,
    unit_kind  text not null check (unit_kind in ('SINGLE', 'BUNK_TOP', 'BUNK_BOTTOM', 'DOUBLE')),
    is_active  boolean not null default true,
    created_at timestamptz not null default now(),
    unique (tenant_id, space_id, label)
);

create table room_type_space (
    tenant_id    uuid not null references tenant (id) on delete cascade,
    room_type_id uuid not null references room_type (id) on delete cascade,
    space_id     uuid not null references space (id) on delete cascade,
    primary key (room_type_id, space_id)
);

-- Indices for FKs and lookups
create index idx_room_type_property on room_type (tenant_id, property_id);
create index idx_space_property on space (tenant_id, property_id);
create index idx_unit_space on unit (tenant_id, space_id);
create index idx_room_type_space_space on room_type_space (tenant_id, space_id);

-- Row Level Security
alter table room_type enable row level security;
alter table room_type force row level security;
create policy room_type_tenant_isolation on room_type
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table space enable row level security;
alter table space force row level security;
create policy space_tenant_isolation on space
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table unit enable row level security;
alter table unit force row level security;
create policy unit_tenant_isolation on unit
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table room_type_space enable row level security;
alter table room_type_space force row level security;
create policy room_type_space_tenant_isolation on room_type_space
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());
