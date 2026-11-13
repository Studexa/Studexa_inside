-- Step 4: school subjects owned by a student.

create table subjects (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    name varchar(100) not null,
    color varchar(20) not null default '#6366f1',
    icon varchar(32),
    archived boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_subjects_user on subjects (user_id);
create index idx_subjects_user_archived on subjects (user_id, archived);
