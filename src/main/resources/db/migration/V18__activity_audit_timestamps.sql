-- Match the existing FocusSession and BossFight entity audit fields.
-- Preserve historical completion times when backfilling existing activities.
ALTER TABLE focus_sessions ADD COLUMN created_at TIMESTAMPTZ;
ALTER TABLE focus_sessions ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE focus_sessions SET created_at = completed_at, updated_at = completed_at;
ALTER TABLE focus_sessions ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE focus_sessions ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE focus_sessions ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE focus_sessions ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE boss_fights ADD COLUMN created_at TIMESTAMPTZ;
ALTER TABLE boss_fights ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE boss_fights SET created_at = completed_at, updated_at = completed_at;
ALTER TABLE boss_fights ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE boss_fights ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE boss_fights ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE boss_fights ALTER COLUMN updated_at SET NOT NULL;
