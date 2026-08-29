-- Phase 4 — conversation PERSISTENCE only.
--
-- Deliberately not the threading model. phase-3-validation.md §9.1 admits the
-- table (it survives a KILL verdict — owner-facing ops still needs a record of
-- what was said) and withholds guest-thread semantics and the WhatsApp mapping
-- until the R0 gate answers.
--
-- `external_ref` is nullable and unused for now. It is the seam where a
-- WhatsApp thread id will attach if the gate passes; it is not a commitment to
-- WhatsApp, and nothing reads it yet.
--
-- This table is also what closes phase-1-review.md finding #8 (client-supplied
-- history is trusted, so a caller can fabricate assistant turns) — but the fix
-- is the server reading history from here instead of the request body, and that
-- is a later step, not this migration.

create table conversation (
    id               uuid primary key     default gen_random_uuid(),
    tenant_id        uuid        not null references tenant (id) on delete cascade,
    property_id      uuid        not null references property (id) on delete cascade,
    external_ref     text,
    started_at       timestamptz not null default now(),
    last_activity_at timestamptz not null default now()
);

create index conversation_tenant_idx on conversation (tenant_id);
create index conversation_property_activity_idx on conversation (property_id, last_activity_at desc);

-- Unique per tenant rather than globally: two tenants must be able to hold the
-- same upstream identifier without colliding.
create unique index conversation_tenant_external_ref_key
    on conversation (tenant_id, external_ref)
    where external_ref is not null;

create table conversation_turn (
    id                uuid primary key     default gen_random_uuid(),
    tenant_id         uuid        not null references tenant (id) on delete cascade,
    conversation_id   uuid        not null references conversation (id) on delete cascade,
    seq               integer     not null check (seq >= 0),
    role              text        not null check (role in ('USER', 'ASSISTANT')),
    content           text        not null,
    escalated         boolean     not null default false,
    model             text,
    prompt_tokens     integer,
    completion_tokens integer,
    total_tokens      integer,
    latency_ms        integer,
    created_at        timestamptz not null default now(),
    unique (conversation_id, seq)
);

create index conversation_turn_tenant_idx on conversation_turn (tenant_id);

-- `role` mirrors com.altstay.api.chat.dto.Role. Keep the two in step.
--
-- The token columns are the input to per-tenant margin (product-roadmap.md §9
-- metric 5, "gross margin per tenant"). Phase 1's decision to return `usage` and
-- `latencyMs` on every response was the first brick of that; this is the second.
comment on column conversation_turn.total_tokens is
    'Feeds per-tenant margin. See product-roadmap.md §9 metric 5.';
