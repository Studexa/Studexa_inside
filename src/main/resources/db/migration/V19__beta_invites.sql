CREATE TABLE beta_invites (
    code_hash varchar(64) PRIMARY KEY,
    email varchar(320) NOT NULL,
    expires_at timestamptz NOT NULL,
    redeemed_at timestamptz,
    revoked_at timestamptz
);

CREATE INDEX beta_invites_email_idx ON beta_invites (email);
