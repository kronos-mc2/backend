CREATE TABLE app_users (
  id VARCHAR(64) PRIMARY KEY,
  email VARCHAR(320) NOT NULL UNIQUE,
  full_name VARCHAR(160) NOT NULL,
  password_hash VARCHAR(200),
  auth_provider VARCHAR(16) NOT NULL CHECK (auth_provider IN ('local', 'google', 'apple')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_users_provider ON app_users (auth_provider);
