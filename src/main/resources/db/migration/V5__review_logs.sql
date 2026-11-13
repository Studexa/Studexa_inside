-- Step 7: review audit log for undo and history.

create table review_logs (
    id uuid primary key,
    client_review_id uuid not null unique,
    card_id uuid not null references cards (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    rating varchar(10) not null check (rating in ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    reviewed_at timestamptz not null default now(),
    ms_taken integer,
    xp_earned integer not null default 0,
    previous_ease double precision,
    previous_interval_days integer,
    previous_reps integer,
    previous_lapses integer,
    previous_due_at timestamptz,
    previous_state varchar(20)
);

create index idx_review_logs_card on review_logs (card_id);
create index idx_review_logs_user on review_logs (user_id);
create index idx_review_logs_reviewed_at on review_logs (reviewed_at);
