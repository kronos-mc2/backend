ALTER TABLE event_media
  ADD COLUMN original_filename TEXT;

UPDATE event_media
SET original_filename = REGEXP_REPLACE(storage_key, '^.*/', '')
WHERE storage_key IS NOT NULL
  AND original_filename IS NULL;
