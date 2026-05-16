CREATE TABLE user_notification_preferences (
  user_id VARCHAR(64) PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
  direct_messages_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  group_messages_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_push_tokens (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  token TEXT NOT NULL,
  platform VARCHAR(16) NOT NULL CHECK (platform IN ('ios', 'android', 'web', 'unknown')),
  device_id TEXT,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  last_registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (token)
);

CREATE INDEX idx_user_push_tokens_user_enabled ON user_push_tokens (user_id, enabled);

CREATE TABLE chat_notification_mutes (
  room_id VARCHAR(64) NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  muted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (room_id, user_id)
);

CREATE INDEX idx_chat_notification_mutes_user_id ON chat_notification_mutes (user_id);
