ALTER TABLE app_users
  ADD COLUMN avatar_storage_key TEXT,
  ADD COLUMN avatar_bucket_name TEXT,
  ADD COLUMN avatar_content_type VARCHAR(120),
  ADD COLUMN avatar_byte_size BIGINT,
  ADD COLUMN account_status VARCHAR(24) NOT NULL DEFAULT 'active',
  ADD COLUMN deletion_requested_at TIMESTAMPTZ,
  ADD COLUMN deletion_scheduled_at TIMESTAMPTZ,
  ADD COLUMN deleted_at TIMESTAMPTZ,
  ADD CONSTRAINT app_users_account_status_check CHECK (account_status IN ('active', 'deletion_pending', 'deleted'));

CREATE TABLE account_deletion_recoveries (
  user_id VARCHAR(64) PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
  original_email VARCHAR(320) NOT NULL UNIQUE,
  original_full_name VARCHAR(160) NOT NULL,
  original_bio TEXT,
  original_avatar_url TEXT,
  original_password_hash VARCHAR(200),
  original_auth_provider VARCHAR(16) NOT NULL CHECK (original_auth_provider IN ('local', 'google', 'apple')),
  requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  scheduled_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_account_deletion_recoveries_scheduled
  ON account_deletion_recoveries (scheduled_at);

CREATE INDEX idx_app_users_account_status
  ON app_users (account_status, deletion_scheduled_at);
