CREATE TABLE events (
  id VARCHAR(64) PRIMARY KEY,
  title_hr TEXT NOT NULL,
  title_en TEXT NOT NULL,
  where_hr TEXT NOT NULL,
  where_en TEXT NOT NULL,
  about_hr TEXT NOT NULL,
  about_en TEXT NOT NULL,
  when_iso TIMESTAMPTZ NOT NULL,
  event_type VARCHAR(16) NOT NULL CHECK (event_type IN ('nearby', 'joined', 'created')),
  latitude DOUBLE PRECISION NOT NULL,
  longitude DOUBLE PRECISION NOT NULL,
  entrance_latitude DOUBLE PRECISION,
  entrance_longitude DOUBLE PRECISION,
  entry_instructions_hr TEXT,
  entry_instructions_en TEXT,
  visibility VARCHAR(16) NOT NULL DEFAULT 'public' CHECK (visibility IN ('public', 'private')),
  participant_count INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_events_when_iso ON events (when_iso);
CREATE INDEX idx_events_type ON events (event_type);

CREATE TABLE friends (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  status_hr TEXT NOT NULL,
  status_en TEXT NOT NULL
);

CREATE TABLE conversations (
  id VARCHAR(64) PRIMARY KEY,
  contact VARCHAR(120) NOT NULL,
  last_message_hr TEXT NOT NULL,
  last_message_en TEXT NOT NULL,
  time_label VARCHAR(64) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO events (
  id,
  title_hr,
  title_en,
  where_hr,
  where_en,
  about_hr,
  about_en,
  when_iso,
  event_type,
  latitude,
  longitude,
  participant_count,
  visibility
)
VALUES
  (
    '1',
    'Cosmo party dobrodoslice',
    'Cosmo''s welcome party',
    'Cvjetni trg, Zagreb',
    'Cvjetni Square, Zagreb',
    'Lezerno okupljanje uz DJ set i dobro drustvo.',
    'Casual gathering with a DJ set and friends.',
    '2026-04-12T18:30:00.000Z',
    'nearby',
    45.8134,
    15.9778,
    32,
    'public'
  ),
  (
    '2',
    'Fiesta ORLA',
    'ORLA Fiesta',
    'Bundek, Zagreb',
    'Bundek Lake, Zagreb',
    'Open-air event uz hranu, pice i live nastup.',
    'Open-air event with food, drinks and live music.',
    '2026-04-25T17:00:00.000Z',
    'joined',
    45.7887,
    15.9903,
    88,
    'public'
  ),
  (
    '3',
    'Aullidos Tour Zagreb',
    'Aullidos Tour Zagreb',
    'Arena Zagreb',
    'Zagreb Arena',
    'Vecernji koncert i after-event.',
    'Evening concert with an after-event.',
    '2026-05-09T19:30:00.000Z',
    'nearby',
    45.7714,
    15.9419,
    350,
    'public'
  ),
  (
    '4',
    'Iron Maiden Night',
    'Iron Maiden Night',
    'Tvornica Kulture',
    'Tvornica Kulture',
    'Tribute night s tematskim DJ-em.',
    'Tribute night with themed DJ lineup.',
    '2026-06-18T20:00:00.000Z',
    'created',
    45.8067,
    15.9661,
    120,
    'public'
  );

INSERT INTO friends (id, name, status_hr, status_en)
VALUES
  ('u1', 'Ana', 'Online i spremna za izlazak', 'Online and ready to go out'),
  ('u2', 'Marko', 'Dolazi na ORLA event', 'Joining ORLA event'),
  ('u3', 'Lana', 'Na putu prema centru', 'Heading downtown');

INSERT INTO conversations (id, contact, last_message_hr, last_message_en, time_label)
VALUES
  ('c1', 'Ana', 'Vidimo se u subotu!', 'See you on Saturday!', '09:12'),
  ('c2', 'Marko', 'Stizem za 5 min', 'I will be there in 5 min', '08:45'),
  ('c3', 'Lana', 'Super event jucer', 'Great event yesterday', 'Yesterday');
