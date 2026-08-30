-- Phase 5, Milestone 1 — Property expansion and amenity reference vocabulary.

-- 1. Property table expansion
alter table property
    add column legal_name     text,
    add column description    text,
    add column status         text not null default 'ACTIVE'
                                   check (status in ('ACTIVE', 'INACTIVE')),
    add column timezone       text,        -- IANA, e.g. 'Asia/Kolkata'. NOT NULL after backfill
    add column currency_code  char(3),     -- ISO 4217.                  NOT NULL after backfill
    add column country_code   char(2),     -- ISO 3166-1 alpha-2
    add column address_line1  text,
    add column address_line2  text,
    add column city           text,
    add column state_region   text,
    add column postal_code    text,
    add column contact_email  text,
    add column contact_phone  text,
    add column check_in_time  time not null default '14:00',
    add column check_out_time time not null default '11:00',
    add column tax_rate_bps   integer not null default 0
                                   check (tax_rate_bps between 0 and 10000);

-- Backfill mechanics per §11.1 & pre-approved correction 3:
-- Disable RLS to avoid silent zero-row backfill, backfill existing rows, re-enable + re-force RLS.
alter table property disable row level security;

update property
set timezone = 'Asia/Kolkata',
    currency_code = 'INR'
where timezone is null or currency_code is null;

alter table property enable row level security;
alter table property force row level security;

-- Assert no row has NULL timezone or currency_code before setting NOT NULL
do $$
declare
    null_count integer;
begin
    select count(*) into null_count from property where timezone is null or currency_code is null;
    if null_count > 0 then
        raise exception 'Property backfill failed: % rows have NULL timezone or currency_code', null_count;
    end if;
end;
$$;

alter table property alter column timezone set not null;
alter table property alter column currency_code set not null;

-- 2. Amenity reference vocabulary (No tenant_id, no RLS, no PII)
create table amenity (
    code     text primary key,
    label    text not null,
    category text not null check (category in ('CONNECTIVITY', 'FOOD', 'FACILITY', 'SERVICE'))
);

comment on table amenity is
    'Reference vocabulary for property amenities. Public reference data, no RLS, no tenant_id, no PII.';

-- Seed amenity reference data
insert into amenity (code, label, category) values
    ('WIFI', 'Wi-Fi', 'CONNECTIVITY'),
    ('HIGH_SPEED_WIFI', 'High-Speed Wi-Fi', 'CONNECTIVITY'),
    ('COWORKING_SPACE', 'Coworking Space', 'CONNECTIVITY'),
    ('BREAKFAST', 'Breakfast Included', 'FOOD'),
    ('COMMUNAL_KITCHEN', 'Shared Kitchen', 'FOOD'),
    ('CAFE', 'Café', 'FOOD'),
    ('BAR', 'Bar', 'FOOD'),
    ('WATER_REFILL', 'Filtered Water Station', 'FOOD'),
    ('AIR_CONDITIONING', 'Air Conditioning', 'FACILITY'),
    ('LOCKERS', 'Secure Lockers', 'FACILITY'),
    ('HOT_SHOWERS', '24/7 Hot Showers', 'FACILITY'),
    ('COMMON_ROOM', 'Common Lounge', 'FACILITY'),
    ('ROOFTOP_TERRACE', 'Rooftop Terrace', 'FACILITY'),
    ('SWIMMING_POOL', 'Swimming Pool', 'FACILITY'),
    ('PARKING', 'Free Parking', 'FACILITY'),
    ('GARDEN', 'Garden / Courtyard', 'FACILITY'),
    ('LUGGAGE_STORAGE', 'Luggage Storage', 'FACILITY'),
    ('LAUNDRY', 'Self-Service Laundry', 'SERVICE'),
    ('TOWELS_INCLUDED', 'Towels Included', 'SERVICE'),
    ('FRONT_DESK_24H', '24-Hour Front Desk', 'SERVICE'),
    ('AIRPORT_TRANSFER', 'Airport Shuttle / Transfer', 'SERVICE'),
    ('BICYCLE_RENTAL', 'Bicycle Rental', 'SERVICE')
on conflict (code) do nothing;

-- 3. Property amenity mapping (tenant-scoped, RLS as V4)
create table property_amenity (
    tenant_id    uuid not null references tenant (id) on delete cascade,
    property_id  uuid not null references property (id) on delete cascade,
    amenity_code text not null references amenity (code),
    primary key (property_id, amenity_code)
);

create index property_amenity_tenant_idx on property_amenity (tenant_id);

alter table property_amenity enable row level security;
alter table property_amenity force row level security;

create policy tenant_isolation on property_amenity
    for all
    using (tenant_id = app_current_tenant())
    with check (tenant_id = app_current_tenant());
