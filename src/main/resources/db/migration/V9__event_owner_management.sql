CREATE TABLE event_blocks (
  event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  created_by_user_id VARCHAR(64) REFERENCES app_users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (event_id, user_id)
);

CREATE INDEX idx_event_blocks_user_id ON event_blocks (user_id);
