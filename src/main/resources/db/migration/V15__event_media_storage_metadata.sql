ALTER TABLE event_media
  ADD COLUMN storage_key TEXT,
  ADD COLUMN bucket_name TEXT,
  ADD COLUMN content_type VARCHAR(128),
  ADD COLUMN byte_size BIGINT,
  ADD COLUMN width INTEGER,
  ADD COLUMN height INTEGER;

ALTER TABLE event_media
  ADD CONSTRAINT event_media_byte_size_check CHECK (byte_size IS NULL OR byte_size > 0),
  ADD CONSTRAINT event_media_dimensions_check CHECK (
    (width IS NULL AND height IS NULL)
    OR (width IS NOT NULL AND width > 0 AND height IS NOT NULL AND height > 0)
  );

CREATE INDEX idx_event_media_storage_key ON event_media (storage_key);
