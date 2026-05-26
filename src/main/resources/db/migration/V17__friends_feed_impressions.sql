CREATE TABLE event_feed_impressions (
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  shown_count INTEGER NOT NULL DEFAULT 0,
  last_shown_at TIMESTAMPTZ,
  interacted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, event_id)
);

CREATE INDEX idx_event_feed_impressions_user_interaction
  ON event_feed_impressions (user_id, interacted_at, shown_count, last_shown_at DESC);
