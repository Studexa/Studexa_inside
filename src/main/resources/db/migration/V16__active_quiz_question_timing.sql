ALTER TABLE sessions
    ADD COLUMN current_question_id UUID,
    ADD COLUMN current_question_started_at TIMESTAMPTZ,
    ADD CONSTRAINT fk_sessions_current_question
        FOREIGN KEY (current_question_id) REFERENCES questions(id) ON DELETE SET NULL;
