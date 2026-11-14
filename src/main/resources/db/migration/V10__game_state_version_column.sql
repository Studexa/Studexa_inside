-- Step 13: add the optimistic-locking version column to game_state.
-- The GameState entity carries @Version (see V6 optimistic locking intent),
-- but the V7 migration never created the column, so schema validation fails.
alter table game_state
    add column if not exists version bigint not null default 0;

create index if not exists idx_game_state_version on game_state (version);
