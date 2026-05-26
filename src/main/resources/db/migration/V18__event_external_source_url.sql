ALTER TABLE events
  ADD COLUMN source_url TEXT;

UPDATE events
SET source_url = COALESCE(
  substring(about_hr FROM 'Izvor: (https?://[^[:space:]]+)'),
  substring(about_en FROM 'Source: (https?://[^[:space:]]+)')
)
WHERE source_url IS NULL
  AND (
    about_hr ~ 'Izvor: https?://'
    OR about_en ~ 'Source: https?://'
  );

UPDATE events
SET about_hr = btrim(regexp_replace(about_hr, '[[:space:]]*Izvor: https?://[^[:space:]]+[[:space:]]*$', '', ''))
WHERE about_hr ~ 'Izvor: https?://';

UPDATE events
SET about_en = btrim(regexp_replace(about_en, '[[:space:]]*Source: https?://[^[:space:]]+[[:space:]]*$', '', ''))
WHERE about_en ~ 'Source: https?://';
