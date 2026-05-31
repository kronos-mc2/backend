ALTER TABLE event_media
  ADD COLUMN thumbnail_storage_key TEXT,
  ADD COLUMN thumbnail_bucket_name TEXT,
  ADD COLUMN thumbnail_content_type TEXT,
  ADD COLUMN thumbnail_byte_size BIGINT,
  ADD COLUMN thumbnail_width INTEGER,
  ADD COLUMN thumbnail_height INTEGER;

CREATE INDEX idx_messages_room_id_id ON messages (room_id, id);
