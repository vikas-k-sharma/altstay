-- Phase 4, Track C — the knowledge base moves off localStorage.
--
-- product-roadmap.md R1 requires it "versioned, with an edit history", so the
-- version table is the design rather than an add-on: every save writes a new
-- knowledge_base_version row and knowledge_base repoints at it.
--
-- This mirrors something already true of the capture format — a `kb` record is
-- written whenever the text changes (verified 2026-08-29). The same edit that
-- produces a capture record produces a version row, which turns act 3 of a beta
-- session into queryable history instead of a diff someone reconstructs later.

create table property (
    id         uuid primary key     default gen_random_uuid(),
    tenant_id  uuid        not null references tenant (id) on delete cascade,
    name       text        not null,
    slug       text        not null,
    created_at timestamptz not null default now(),
    unique (tenant_id, slug)
);

create index property_tenant_idx on property (tenant_id);

comment on table property is
    'A tenant may run more than one property; the concierge answers per property.';

create table knowledge_base (
    id                 uuid primary key     default gen_random_uuid(),
    tenant_id          uuid        not null references tenant (id) on delete cascade,
    property_id        uuid        not null references property (id) on delete cascade,
    current_version_id uuid,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    unique (tenant_id, property_id)
);

create index knowledge_base_tenant_idx on knowledge_base (tenant_id);

create table knowledge_base_version (
    id                uuid primary key     default gen_random_uuid(),
    tenant_id         uuid        not null references tenant (id) on delete cascade,
    knowledge_base_id uuid        not null references knowledge_base (id) on delete cascade,
    version_no        integer     not null check (version_no > 0),
    content           text        not null,
    content_sha256    text        not null,
    char_count        integer     not null check (char_count between 1 and 20000),
    authored_by       uuid references app_user (id) on delete set null,
    created_at        timestamptz not null default now(),
    unique (knowledge_base_id, version_no)
);

create index knowledge_base_version_tenant_idx on knowledge_base_version (tenant_id);
create index knowledge_base_version_kb_idx on knowledge_base_version (knowledge_base_id, version_no desc);

-- The 20,000 ceiling matches ChatRequest's @Size(max = 20_000) and the inline
-- block in the admin panel. Kept in the schema so a bad write cannot bypass the
-- API. If the product limit moves, this constraint moves with it.
comment on column knowledge_base_version.char_count is
    'Mirrors ChatRequest @Size(max = 20_000); keep the two in step.';

-- Added after the version table exists, so the two tables can reference each
-- other without an ordering problem.
alter table knowledge_base
    add constraint knowledge_base_current_version_fk
        foreign key (current_version_id)
            references knowledge_base_version (id)
            on delete set null;
