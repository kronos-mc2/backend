ALTER TABLE events
  ADD COLUMN creator_user_id VARCHAR(64),
  ADD COLUMN address TEXT,
  ADD COLUMN start_at TIMESTAMPTZ,
  ADD COLUMN end_at TIMESTAMPTZ,
  ADD COLUMN attendance_mode VARCHAR(16) NOT NULL DEFAULT 'open',
  ADD COLUMN price_amount NUMERIC(10, 2),
  ADD COLUMN price_currency VARCHAR(3),
  ADD COLUMN capacity INTEGER,
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'published',
  ADD COLUMN organizer_rating_average NUMERIC(3, 2) NOT NULL DEFAULT 0,
  ADD COLUMN organizer_rating_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE events
SET
  address = COALESCE(address, where_hr),
  start_at = COALESCE(start_at, when_iso),
  visibility = CASE WHEN visibility = 'private' THEN 'friends' ELSE visibility END;

ALTER TABLE events
  ALTER COLUMN address SET NOT NULL,
  ALTER COLUMN start_at SET NOT NULL,
  DROP CONSTRAINT IF EXISTS events_visibility_check,
  ADD CONSTRAINT events_visibility_check CHECK (visibility IN ('public', 'friends')),
  ADD CONSTRAINT events_attendance_mode_check CHECK (attendance_mode IN ('open', 'waitlist', 'paid')),
  ADD CONSTRAINT events_status_check CHECK (status IN ('draft', 'published', 'cancelled', 'finished')),
  ADD CONSTRAINT events_price_check CHECK (
    (attendance_mode <> 'paid' AND price_amount IS NULL AND price_currency IS NULL)
    OR (attendance_mode = 'paid' AND price_amount IS NOT NULL AND price_amount >= 0 AND price_currency IS NOT NULL)
  ),
  ADD CONSTRAINT events_capacity_check CHECK (capacity IS NULL OR capacity > 0),
  ADD CONSTRAINT events_rating_average_check CHECK (organizer_rating_average >= 0 AND organizer_rating_average <= 5),
  ADD CONSTRAINT events_rating_count_check CHECK (organizer_rating_count >= 0),
  ADD CONSTRAINT fk_events_creator_user FOREIGN KEY (creator_user_id) REFERENCES app_users(id) ON DELETE SET NULL;

CREATE INDEX idx_events_creator_user_id ON events (creator_user_id);
CREATE INDEX idx_events_start_at ON events (start_at);
CREATE INDEX idx_events_visibility_status ON events (visibility, status);
CREATE INDEX idx_events_attendance_mode ON events (attendance_mode);

CREATE TABLE event_media (
  id VARCHAR(64) PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  media_type VARCHAR(16) NOT NULL CHECK (media_type IN ('image', 'video')),
  url TEXT NOT NULL,
  thumbnail_url TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_event_media_event_id ON event_media (event_id, sort_order);

CREATE TABLE event_participants (
  event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  status VARCHAR(16) NOT NULL CHECK (status IN ('joined', 'left', 'waitlisted', 'approved', 'rejected')),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  approved_at TIMESTAMPTZ,
  PRIMARY KEY (event_id, user_id)
);

CREATE INDEX idx_event_participants_user_id ON event_participants (user_id);
CREATE INDEX idx_event_participants_status ON event_participants (status);

CREATE TABLE event_likes (
  event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (event_id, user_id)
);

CREATE INDEX idx_event_likes_user_id ON event_likes (user_id);

CREATE TABLE event_organizer_ratings (
  event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  organizer_user_id VARCHAR(64) REFERENCES app_users(id) ON DELETE SET NULL,
  rater_user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
  comment TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (event_id, rater_user_id)
);

CREATE INDEX idx_event_organizer_ratings_organizer ON event_organizer_ratings (organizer_user_id);
