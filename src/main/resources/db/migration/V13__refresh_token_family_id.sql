-- Step 15: add family_id to refresh_tokens for token-family revocation.
-- When a rotated/revoked refresh token is reused, all tokens in the same
-- family are revoked, forcing the user to log in again.
alter table refresh_tokens
    add column if not exists family_id uuid;

create index if not exists idx_refresh_tokens_family on refresh_tokens (family_id);
