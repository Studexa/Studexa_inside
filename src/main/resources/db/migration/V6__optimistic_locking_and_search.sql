-- Optimistic locking for card progress and Khmer-friendly trigram search.

-- @Version column on card_progress: prevents lost updates when two review
-- requests touch the same card at the same time.
ALTER TABLE card_progress ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Trigram search (also works well for Khmer text). The extension needs to be
-- created once by a superuser; IF NOT EXISTS makes this a no-op afterwards.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_cards_front_trgm ON cards USING gin (front gin_trgm_ops);
CREATE INDEX idx_cards_back_trgm ON cards USING gin (back gin_trgm_ops);
CREATE INDEX idx_decks_title_trgm ON decks USING gin (title gin_trgm_ops);
