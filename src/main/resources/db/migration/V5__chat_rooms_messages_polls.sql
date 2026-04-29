CREATE TABLE chat_rooms (
  id VARCHAR(64) PRIMARY KEY,
  room_type VARCHAR(16) NOT NULL CHECK (room_type IN ('direct', 'group', 'event')),
  title TEXT,
  event_id VARCHAR(64) REFERENCES events(id) ON DELETE CASCADE,
  is_admin_only BOOLEAN NOT NULL DEFAULT FALSE,
  created_by_user_id VARCHAR(64) REFERENCES app_users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_rooms_type ON chat_rooms (room_type);
CREATE INDEX idx_chat_rooms_event_id ON chat_rooms (event_id);
CREATE INDEX idx_chat_rooms_updated_at ON chat_rooms (updated_at DESC);

CREATE TABLE chat_members (
  room_id VARCHAR(64) NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  role VARCHAR(16) NOT NULL CHECK (role IN ('owner', 'admin', 'member')),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (room_id, user_id)
);

CREATE INDEX idx_chat_members_user_id ON chat_members (user_id);

CREATE TABLE messages (
  id VARCHAR(64) PRIMARY KEY,
  room_id VARCHAR(64) NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
  sender_user_id VARCHAR(64) REFERENCES app_users(id) ON DELETE SET NULL,
  message_type VARCHAR(24) NOT NULL CHECK (message_type IN ('text', 'event_share', 'poll')),
  body TEXT,
  event_id VARCHAR(64) REFERENCES events(id) ON DELETE SET NULL,
  poll_id VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_room_created_at ON messages (room_id, created_at DESC, id DESC);
CREATE INDEX idx_messages_event_id ON messages (event_id);

CREATE TABLE message_reads (
  room_id VARCHAR(64) NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  last_read_message_id VARCHAR(64) REFERENCES messages(id) ON DELETE SET NULL,
  read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (room_id, user_id)
);

CREATE TABLE polls (
  id VARCHAR(64) PRIMARY KEY,
  room_id VARCHAR(64) NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
  question TEXT NOT NULL,
  allow_multiple BOOLEAN NOT NULL DEFAULT FALSE,
  created_by_user_id VARCHAR(64) REFERENCES app_users(id) ON DELETE SET NULL,
  closes_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_polls_room_id ON polls (room_id);

CREATE TABLE poll_options (
  id VARCHAR(64) PRIMARY KEY,
  poll_id VARCHAR(64) NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
  text TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_poll_options_poll_id ON poll_options (poll_id, sort_order);

CREATE TABLE poll_votes (
  poll_id VARCHAR(64) NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
  option_id VARCHAR(64) NOT NULL REFERENCES poll_options(id) ON DELETE CASCADE,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (poll_id, option_id, user_id)
);

CREATE INDEX idx_poll_votes_user_id ON poll_votes (user_id);

ALTER TABLE messages
  ADD CONSTRAINT fk_messages_poll FOREIGN KEY (poll_id) REFERENCES polls(id) ON DELETE SET NULL;

INSERT INTO chat_rooms (id, room_type, title, is_admin_only, created_at, updated_at)
SELECT
  id,
  'direct',
  contact,
  FALSE,
  updated_at,
  updated_at
FROM conversations
ON CONFLICT (id) DO NOTHING;

INSERT INTO messages (id, room_id, message_type, body, created_at)
SELECT
  'legacy-' || id,
  id,
  'text',
  last_message_hr,
  updated_at
FROM conversations
ON CONFLICT (id) DO NOTHING;
