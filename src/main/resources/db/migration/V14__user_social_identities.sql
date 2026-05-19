CREATE TABLE user_social_identities (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  provider VARCHAR(16) NOT NULL CHECK (provider IN ('google', 'apple')),
  provider_subject VARCHAR(255) NOT NULL,
  email VARCHAR(320) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (provider, provider_subject),
  UNIQUE (provider, user_id)
);

CREATE INDEX idx_user_social_identities_user ON user_social_identities (user_id);
