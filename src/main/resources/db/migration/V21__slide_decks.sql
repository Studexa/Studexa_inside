CREATE TABLE slide_decks (
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id UUID NOT NULL,
    quiz_id UUID NOT NULL UNIQUE REFERENCES quizzes(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    slides JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_id, id)
);

CREATE INDEX idx_slide_decks_owner_created ON slide_decks (owner_id, created_at DESC, id);
