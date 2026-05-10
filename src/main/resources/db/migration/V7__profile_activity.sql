ALTER TABLE app_users
  ADD COLUMN bio TEXT,
  ADD COLUMN avatar_url TEXT;

CREATE TABLE transactions (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  event_id VARCHAR(64) REFERENCES events(id) ON DELETE SET NULL,
  transaction_type VARCHAR(24) NOT NULL DEFAULT 'ticket',
  amount NUMERIC(10, 2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(24) NOT NULL CHECK (status IN ('pending', 'paid', 'failed', 'refunded')),
  description TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_user_created_at ON transactions (user_id, created_at DESC);
CREATE INDEX idx_transactions_event_id ON transactions (event_id);
