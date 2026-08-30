-- Phase 5 review fix — modifying a booking must not destroy its operational history.
--
-- §4 chose a PARTIAL exclusion constraint, `where (released_at is null)`, for a stated reason:
-- "Cancelling releases the bed without deleting the row, so 'which bed was that guest in'
-- survives a cancellation. Deleting the rows would be simpler and would lose the operational
-- history that makes a PMS a system of record."
--
-- The modify path defeated that. It released the old allocations and then DELETED the old
-- booking_line rows, and allocation.booking_line_id cascades on delete — so every allocation the
-- guest had ever held disappeared on the first date change. The release was real and the record
-- of it was not.
--
-- Old lines are now superseded rather than deleted: they stay, their allocations stay released,
-- and the current state of the booking is the set of lines where superseded_at is null.
alter table booking_line
    add column superseded_at timestamptz;

comment on column booking_line.superseded_at is
    'Set when a modification replaces this line. Non-null rows are history: excluded from totals, '
    'from the current line list, and from allocation. Their allocations carry released_at.';

-- Partial index: every read of a booking''s current lines carries this predicate.
create index booking_line_active_idx
    on booking_line (tenant_id, booking_id) where superseded_at is null;
