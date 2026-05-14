ALTER TABLE events
  ADD COLUMN event_rating_average NUMERIC(3, 2) NOT NULL DEFAULT 0,
  ADD COLUMN event_rating_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE events
  ADD CONSTRAINT events_event_rating_average_check CHECK (event_rating_average >= 0 AND event_rating_average <= 5),
  ADD CONSTRAINT events_event_rating_count_check CHECK (event_rating_count >= 0);

CREATE TABLE event_ratings (
  event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  rater_user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
  comment TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (event_id, rater_user_id)
);

CREATE TABLE app_notifications (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  notification_type VARCHAR(48) NOT NULL,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  event_id VARCHAR(64) REFERENCES events(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  read_at TIMESTAMPTZ
);

CREATE INDEX idx_event_ratings_user_id ON event_ratings (rater_user_id);
CREATE INDEX idx_app_notifications_user_created_at ON app_notifications (user_id, created_at DESC);
CREATE INDEX idx_app_notifications_event_id ON app_notifications (event_id);
