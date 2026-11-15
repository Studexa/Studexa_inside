-- Step 16: persistence for notes, exams, tasks, focus sessions, and boss fights.
-- These were previously local-only (Zustand mock store). Now they are server-backed
-- so data syncs across devices and XP awards can be verified server-side.

-- Notes: rich-text study notes owned by a student.
create table notes (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    title varchar(200) not null,
    content text not null default '',
    subject varchar(100),
    updated_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create index idx_notes_user on notes (user_id);
create index idx_notes_user_updated_at on notes (user_id, updated_at desc);

-- Exams: upcoming exams with subject, date, and importance.
create table exams (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    title varchar(200) not null,
    subject varchar(200),
    exam_date date not null,
    importance varchar(20) not null default 'medium' check (importance in ('low', 'medium', 'high')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_exams_user on exams (user_id);
create index idx_exams_user_date on exams (user_id, exam_date);

-- Tasks: daily todo items.
create table tasks (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    title varchar(200) not null,
    task_date date not null,
    done boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_tasks_user on tasks (user_id);
create index idx_tasks_user_date on tasks (user_id, task_date);

-- Focus sessions: completed timer sessions for XP and streak tracking.
create table focus_sessions (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    minutes integer not null check (minutes > 0),
    completed_at timestamptz not null default now()
);

create index idx_focus_sessions_user on focus_sessions (user_id);
create index idx_focus_sessions_user_completed_at on focus_sessions (user_id, completed_at desc);

-- Boss fights: win/loss record for XP and badge tracking.
create table boss_fights (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    won boolean not null,
    questions_count integer not null,
    hearts_remaining integer not null,
    completed_at timestamptz not null default now()
);

create index idx_boss_fights_user on boss_fights (user_id);
create index idx_boss_fights_user_completed_at on boss_fights (user_id, completed_at desc);
