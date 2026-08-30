-- V8__bookings.sql
-- PMS Core: Guests, bookings, booking lines, and status history.

create table guest (
    id            uuid primary key default gen_random_uuid(),
    tenant_id     uuid not null references tenant (id) on delete cascade,
    full_name     text not null,
    email         text,
    phone         text,
    country_code  char(2),
    date_of_birth date,
    notes         text,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create index guest_tenant_email_idx on guest (tenant_id, lower(email));

create table booking (
    id                  uuid primary key default gen_random_uuid(),
    tenant_id           uuid not null references tenant (id) on delete cascade,
    property_id         uuid not null references property (id) on delete cascade,
    reference           text not null,
    guest_id            uuid not null references guest (id),
    status              text not null check (status in
                            ('BOOKED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW')),
    source              text not null check (source in ('DIRECT', 'WALK_IN', 'PHONE', 'OTA')),
    check_in            date not null,
    check_out           date not null check (check_out > check_in),
    adults              integer not null default 1 check (adults >= 1),
    children            integer not null default 0 check (children >= 0),
    currency_code       char(3) not null,
    subtotal_minor      bigint not null check (subtotal_minor >= 0),
    tax_minor           bigint not null default 0 check (tax_minor >= 0),
    total_minor         bigint not null check (total_minor >= 0),
    amount_paid_minor   bigint not null default 0 check (amount_paid_minor >= 0),
    payment_state       text not null default 'UNPAID'
                            check (payment_state in ('UNPAID', 'PARTIAL', 'PAID')),
    idempotency_key     text,
    notes               text,
    created_by          uuid references app_user (id) on delete set null,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    cancelled_at        timestamptz,
    cancellation_reason text,
    unique (tenant_id, reference)
);

create unique index booking_tenant_idempotency_key
    on booking (tenant_id, idempotency_key) where idempotency_key is not null;

create index idx_booking_property on booking (tenant_id, property_id);
create index idx_booking_guest on booking (tenant_id, guest_id);
create index idx_booking_status on booking (tenant_id, status);
create index idx_booking_dates on booking (tenant_id, check_in, check_out);

create table booking_line (
    id           uuid primary key default gen_random_uuid(),
    tenant_id    uuid not null references tenant (id) on delete cascade,
    booking_id   uuid not null references booking (id) on delete cascade,
    room_type_id uuid not null references room_type (id),
    space_id     uuid references space (id),
    check_in     date not null,
    check_out    date not null check (check_out > check_in),
    unit_count   integer not null default 1 check (unit_count >= 1),
    amount_minor bigint not null check (amount_minor >= 0),
    created_at   timestamptz not null default now()
);

create index idx_booking_line_booking on booking_line (tenant_id, booking_id);
create index idx_booking_line_room_type on booking_line (tenant_id, room_type_id);

create table booking_status_history (
    id          uuid primary key default gen_random_uuid(),
    tenant_id   uuid not null references tenant (id) on delete cascade,
    booking_id  uuid not null references booking (id) on delete cascade,
    from_status text,
    to_status   text not null,
    changed_by  uuid references app_user (id) on delete set null,
    reason      text,
    changed_at  timestamptz not null default now()
);

create index idx_booking_status_history_booking on booking_status_history (tenant_id, booking_id);

-- Row Level Security
alter table guest enable row level security;
alter table guest force row level security;
create policy guest_tenant_isolation on guest
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table booking enable row level security;
alter table booking force row level security;
create policy booking_tenant_isolation on booking
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table booking_line enable row level security;
alter table booking_line force row level security;
create policy booking_line_tenant_isolation on booking_line
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());

alter table booking_status_history enable row level security;
alter table booking_status_history force row level security;
create policy booking_status_history_tenant_isolation on booking_status_history
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());
