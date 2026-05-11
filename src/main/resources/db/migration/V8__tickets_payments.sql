CREATE TABLE event_ticket_products (
  id VARCHAR(64) PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  name VARCHAR(180) NOT NULL,
  unit_amount NUMERIC(10, 2) NOT NULL CHECK (unit_amount >= 0),
  currency VARCHAR(3) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT event_ticket_products_event_unique UNIQUE (event_id)
);

CREATE TABLE ticket_orders (
  id VARCHAR(64) PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  ticket_product_id VARCHAR(64) NOT NULL REFERENCES event_ticket_products(id) ON DELETE RESTRICT,
  provider VARCHAR(32) NOT NULL,
  provider_order_id VARCHAR(128),
  provider_payment_id VARCHAR(128),
  amount NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(24) NOT NULL CHECK (status IN ('pending', 'succeeded', 'cancelled', 'failed', 'expired')),
  checkout_url TEXT,
  client_secret TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ
);

CREATE TABLE payments (
  id VARCHAR(64) PRIMARY KEY,
  order_id VARCHAR(64) NOT NULL REFERENCES ticket_orders(id) ON DELETE CASCADE,
  provider VARCHAR(32) NOT NULL,
  provider_payment_id VARCHAR(128),
  amount NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(32) NOT NULL CHECK (status IN ('requires_confirmation', 'processing', 'succeeded', 'failed', 'cancelled')),
  failure_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE transactions
  ADD COLUMN order_id VARCHAR(64) REFERENCES ticket_orders(id) ON DELETE SET NULL,
  ADD COLUMN payment_provider VARCHAR(32),
  ADD COLUMN provider_reference VARCHAR(128);

INSERT INTO event_ticket_products (
  id,
  event_id,
  name,
  unit_amount,
  currency
)
SELECT
  'ticket-' || LEFT(e.id, 57),
  e.id,
  LEFT(e.title_hr, 180),
  e.price_amount,
  e.price_currency
FROM events e
WHERE e.attendance_mode = 'paid'
  AND e.price_amount IS NOT NULL
  AND e.price_currency IS NOT NULL
ON CONFLICT (event_id) DO NOTHING;

CREATE INDEX idx_event_ticket_products_event_id ON event_ticket_products (event_id);
CREATE INDEX idx_ticket_orders_user_created_at ON ticket_orders (user_id, created_at DESC);
CREATE INDEX idx_ticket_orders_event_user ON ticket_orders (event_id, user_id);
CREATE INDEX idx_ticket_orders_status ON ticket_orders (status);
CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE INDEX idx_transactions_order_id ON transactions (order_id);
