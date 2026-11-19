create table note_attachments (
    id uuid primary key,
    note_id uuid not null references notes (id) on delete cascade,
    filename varchar(255) not null,
    size_bytes integer not null check (size_bytes > 0),
    file_bytes bytea not null,
    extracted_text text not null,
    text_truncated boolean not null,
    created_at timestamptz not null default now()
);

create index idx_note_attachments_note on note_attachments (note_id);
