CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY,
  phone TEXT UNIQUE NOT NULL,
  role TEXT NOT NULL DEFAULT 'farmer' CHECK (role IN ('farmer','buyer','processor','admin')),
  name TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS listings (
  id UUID PRIMARY KEY,
  farmer_id UUID NOT NULL REFERENCES users(id), crop_name TEXT NOT NULL,
  quantity NUMERIC NOT NULL CHECK (quantity > 0), unit TEXT NOT NULL,
  quality TEXT, price_per_unit NUMERIC NOT NULL CHECK (price_per_unit >= 0),
  location TEXT, state TEXT, description TEXT, status TEXT NOT NULL DEFAULT 'active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS buy_requests (
  id UUID PRIMARY KEY,
  buyer_id UUID NOT NULL REFERENCES users(id), crop_name TEXT NOT NULL,
  quantity NUMERIC NOT NULL CHECK (quantity > 0), unit TEXT NOT NULL,
  max_price_per_unit NUMERIC NOT NULL CHECK (max_price_per_unit >= 0),
  location TEXT, state TEXT, status TEXT NOT NULL DEFAULT 'open',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS cold_storage_lots (
  id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), crop_name TEXT NOT NULL,
  quantity NUMERIC NOT NULL CHECK (quantity > 0), unit TEXT NOT NULL,
  storage_name TEXT NOT NULL, chamber_code TEXT, temperature_c NUMERIC,
  status TEXT NOT NULL DEFAULT 'stored', sync_version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS supply_chain_events (
  id UUID PRIMARY KEY, lot_id UUID NOT NULL REFERENCES cold_storage_lots(id),
  event_type TEXT NOT NULL, note TEXT, latitude NUMERIC, longitude NUMERIC,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS chat_messages (
  id UUID PRIMARY KEY, listing_id UUID REFERENCES listings(id) ON DELETE SET NULL,
  sender_id UUID NOT NULL REFERENCES users(id), recipient_id UUID NOT NULL REFERENCES users(id),
  body TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 2000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS chat_conversation_idx ON chat_messages(listing_id, created_at);
CREATE TABLE IF NOT EXISTS payments (
  id UUID PRIMARY KEY, buyer_id UUID NOT NULL REFERENCES users(id), listing_id UUID NOT NULL REFERENCES listings(id),
  quantity NUMERIC NOT NULL CHECK (quantity > 0), amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
  gateway_order_id TEXT UNIQUE NOT NULL, gateway_payment_id TEXT UNIQUE,
  status TEXT NOT NULL DEFAULT 'created' CHECK (status IN ('created','paid','failed')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), paid_at TIMESTAMPTZ
);
