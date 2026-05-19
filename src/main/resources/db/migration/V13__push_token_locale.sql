ALTER TABLE user_push_tokens
  ADD COLUMN locale VARCHAR(8) NOT NULL DEFAULT 'hr' CHECK (locale IN ('hr', 'en'));
