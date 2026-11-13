CREATE TABLE decks (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_id UUID REFERENCES subjects(id) ON DELETE SET NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    color VARCHAR(20) NOT NULL DEFAULT '#6366f1',
    icon VARCHAR(32),
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    copied_from UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_decks_user_id ON decks (user_id);
CREATE INDEX idx_decks_user_subject ON decks (user_id, subject_id);

CREATE TABLE cards (
    id UUID PRIMARY KEY,
    deck_id UUID NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('BASIC', 'REVERSED', 'CLOZE', 'MULTIPLE_CHOICE')),
    front TEXT NOT NULL,
    back TEXT NOT NULL,
    extra JSONB NOT NULL DEFAULT '{}'::jsonb,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    media_url TEXT,
    starred BOOLEAN NOT NULL DEFAULT FALSE,
    suspended BOOLEAN NOT NULL DEFAULT FALSE,
    source_title VARCHAR(200),
    source_excerpt TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cards_deck_id ON cards (deck_id);
CREATE INDEX idx_cards_user_id ON cards (user_id);

CREATE TABLE card_progress (
    card_id UUID PRIMARY KEY REFERENCES cards(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ease DOUBLE PRECISION NOT NULL DEFAULT 2.5,
    interval_days INTEGER NOT NULL DEFAULT 0,
    reps INTEGER NOT NULL DEFAULT 0,
    lapses INTEGER NOT NULL DEFAULT 0,
    due_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    state VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (state IN ('NEW', 'LEARNING', 'REVIEW')),
    last_reviewed_at TIMESTAMPTZ
);

CREATE INDEX idx_card_progress_user_due ON card_progress (user_id, due_at);

CREATE TABLE ai_usage (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    calls INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, usage_date)
);
