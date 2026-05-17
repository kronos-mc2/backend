CREATE TABLE event_tags (
  event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  tag VARCHAR(40) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (event_id, tag)
);

CREATE INDEX idx_event_tags_tag_lower ON event_tags (LOWER(tag));

CREATE TABLE user_feed_blocks (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  block_type VARCHAR(16) NOT NULL CHECK (block_type IN ('event', 'creator', 'tag')),
  target_id TEXT NOT NULL,
  target_label TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, block_type, target_id)
);

CREATE INDEX idx_user_feed_blocks_user_type ON user_feed_blocks (user_id, block_type, created_at DESC);

ALTER TABLE messages
  ADD COLUMN encrypted_body TEXT,
  ADD COLUMN encryption_nonce TEXT,
  ADD COLUMN encryption_key_id VARCHAR(64),
  ADD COLUMN encryption_version INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN friend_request_id VARCHAR(64);

ALTER TABLE messages
  DROP CONSTRAINT IF EXISTS messages_message_type_check;

ALTER TABLE messages
  ADD CONSTRAINT messages_message_type_check
  CHECK (message_type IN ('text', 'event_share', 'poll', 'friend_request'));

CREATE TABLE friend_requests (
  id VARCHAR(64) PRIMARY KEY,
  requester_user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  recipient_user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  chat_room_id VARCHAR(64) REFERENCES chat_rooms(id) ON DELETE SET NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'rejected')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  responded_at TIMESTAMPTZ,
  CHECK (requester_user_id <> recipient_user_id)
);

CREATE UNIQUE INDEX idx_friend_requests_pair_active
  ON friend_requests (
    LEAST(requester_user_id, recipient_user_id),
    GREATEST(requester_user_id, recipient_user_id)
  )
  WHERE status IN ('pending', 'accepted');

CREATE INDEX idx_friend_requests_recipient_status ON friend_requests (recipient_user_id, status, created_at DESC);

ALTER TABLE messages
  ADD CONSTRAINT fk_messages_friend_request
  FOREIGN KEY (friend_request_id) REFERENCES friend_requests(id) ON DELETE SET NULL;
