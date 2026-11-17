-- Step 12: Quests (daily and weekly) for the Home dashboard.

-- Daily and weekly quests per student. Created lazily on first request of
-- the student's local day (Section 7.7).
create table quests (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    kind varchar(20) not null check (kind in ('daily', 'weekly')),
    code varchar(40) not null, -- warmup, focus, builder, hotStreak, noteTaker, miniGame, consistency, review150
    target integer not null check (target > 0),
    progress integer not null default 0 check (progress >= 0),
    reward_xp integer not null default 0,
    reward_coins integer not null default 0,
    quest_date date not null, -- local date for daily quests; week-start date for weekly
    completed boolean not null default false,
    created_at timestamptz not null default now(),
    unique (user_id, quest_date, code) -- prevent duplicate creation
);

create index idx_quests_user_date on quests (user_id, quest_date);
create index idx_quests_user_completed on quests (user_id, completed);

-- Quest progress events: tracks which events contribute to quest progress.
-- Used to avoid double-counting when syncing offline reviews.
create table quest_events (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users (id) on delete cascade,
    quest_id uuid not null references quests (id) on delete cascade,
    source varchar(40) not null, -- review_card, focus_session, note_create, mini_game
    reference_id uuid, -- clientReviewId or similar idempotency key
    created_at timestamptz not null default now(),
    unique (quest_id, source, reference_id)
);

create index idx_quest_events_user on quest_events (user_id);
create index idx_quest_events_quest on quest_events (quest_id);
