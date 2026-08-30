-- Phase 5 V10: Rates and Rate Calendar
-- Standard hospitality rate model: base rate on room type, overridden per date by rate calendar

create table rate_plan (
    id            uuid primary key default gen_random_uuid(),
    tenant_id     uuid not null references tenant (id) on delete cascade,
    property_id   uuid not null references property (id) on delete cascade,
    room_type_id  uuid not null references room_type (id) on delete cascade,
    code          text not null,
    name          text not null,
    is_default    boolean not null default false,
    is_active     boolean not null default true,
    created_at    timestamptz not null default now(),
    unique (tenant_id, room_type_id, code)
);

create unique index rate_plan_one_default_per_room_type
    on rate_plan (room_type_id) where is_default;

create table rate_calendar (
    tenant_id    uuid not null references tenant (id) on delete cascade,
    rate_plan_id uuid not null references rate_plan (id) on delete cascade,
    stay_date    date not null,
    amount_minor bigint not null check (amount_minor >= 0),
    primary key (rate_plan_id, stay_date)
);

-- Tenancy: enable and force RLS on both tables
alter table rate_plan enable row level security;
alter table rate_plan force row level security;

create policy rate_plan_tenant_isolation on rate_plan
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table rate_calendar enable row level security;
alter table rate_calendar force row level security;

create policy rate_calendar_tenant_isolation on rate_calendar
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());
