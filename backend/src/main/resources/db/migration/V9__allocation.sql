-- V9__allocation.sql
-- PMS Core: Allocation table with GiST exclusion constraint for unit-level date ranges.

-- Assert that btree_gist extension is installed
do $$
begin
    if not exists (select 1 from pg_extension where extname = 'btree_gist') then
        raise exception 'Required extension btree_gist is not installed in database';
    end if;
end $$;

create table allocation (
    id              uuid primary key default gen_random_uuid(),
    tenant_id       uuid not null references tenant (id) on delete cascade,
    unit_id         uuid not null references unit (id),
    booking_line_id uuid not null references booking_line (id) on delete cascade,
    check_in        date not null,
    check_out       date not null check (check_out > check_in),
    -- Half-open [check_in, check_out): checkout day IS bookable by the next guest
    stay_range      daterange generated always as
                        (daterange(check_in, check_out, '[)')) stored,
    released_at     timestamptz,
    created_at      timestamptz not null default now(),

    constraint allocation_no_overlap
        exclude using gist (unit_id with =, stay_range with &&)
        where (released_at is null)
);

create index idx_allocation_booking_line on allocation (tenant_id, booking_line_id);
create index idx_allocation_unit on allocation (tenant_id, unit_id);
create index idx_allocation_dates on allocation (tenant_id, check_in, check_out);

-- Row Level Security
alter table allocation enable row level security;
alter table allocation force row level security;
create policy allocation_tenant_isolation on allocation
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());
