-- Step 15: add reversed column to xp_events for undo support.
-- When a review is undone, the associated XP events are marked reversed
-- instead of deleted, preserving the audit trail. The awardXp() method
-- skips reversed events for idempotency checks.
alter table xp_events
    add column if not exists reversed boolean not null default false;

create index if not exists idx_xp_events_reversed on xp_events (reversed);
