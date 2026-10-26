-- Dukaan Saathi demo schema (PostgreSQL)
-- Backend security rule: derive shop_id from the authenticated session/token.
-- Never trust shop_id from request payloads or LLM output.
--
-- Audit columns: most mutable tables carry created_at / updated_at / created_by with defaults.
-- The seed CSVs omit them, so import with an explicit column list (COPY t(col, ...) FROM ...).

CREATE TABLE shops (
  shop_id TEXT PRIMARY KEY, shop_name TEXT NOT NULL, business_type TEXT NOT NULL,
  city TEXT NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'INR', is_active BOOLEAN NOT NULL DEFAULT TRUE,
  opening_cash_paise BIGINT NOT NULL DEFAULT 0, opening_cash_date DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- OTP login: the phone number is the identity; no password is stored.
CREATE TABLE users (
  user_id TEXT PRIMARY KEY, phone TEXT UNIQUE NOT NULL, email TEXT UNIQUE, display_name TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE user_shops (
  user_id TEXT NOT NULL REFERENCES users(user_id),
  shop_id TEXT NOT NULL REFERENCES shops(shop_id),
  role TEXT NOT NULL CHECK (role IN ('OWNER','MANAGER','STAFF')),
  PRIMARY KEY(user_id, shop_id)
);
CREATE TABLE suppliers (
  supplier_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(shop_id),
  supplier_name TEXT NOT NULL, contact_phone_masked TEXT, payment_terms_days INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT REFERENCES users(user_id),
  UNIQUE(shop_id, supplier_id)
);
CREATE TABLE products (
  product_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(shop_id),
  product_name TEXT NOT NULL, category TEXT NOT NULL, subcategory TEXT, brand TEXT, variant TEXT,
  unit TEXT NOT NULL, unit_purchase_price_paise BIGINT NOT NULL CHECK(unit_purchase_price_paise >= 0),
  unit_selling_price_paise BIGINT NOT NULL CHECK(unit_selling_price_paise >= 0),
  opening_stock NUMERIC(14,3) NOT NULL DEFAULT 0, reorder_level NUMERIC(14,3) NOT NULL DEFAULT 0,
  supplier_id TEXT, is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT REFERENCES users(user_id),
  UNIQUE(shop_id, product_id),
  FOREIGN KEY(shop_id, supplier_id) REFERENCES suppliers(shop_id, supplier_id)
);
CREATE TABLE customers (
  customer_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(shop_id),
  customer_name TEXT NOT NULL, phone_masked TEXT, customer_type TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT REFERENCES users(user_id),
  UNIQUE(shop_id, customer_id)
);
CREATE TABLE product_aliases (
  alias_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL, product_id TEXT NOT NULL,
  alias_text TEXT NOT NULL, language TEXT NOT NULL, source TEXT NOT NULL DEFAULT 'learned',
  normalized_alias TEXT GENERATED ALWAYS AS (lower(trim(alias_text))) STORED,
  UNIQUE(shop_id, normalized_alias),
  FOREIGN KEY(shop_id, product_id) REFERENCES products(shop_id, product_id)
);
-- Reports must filter status = 'COMPLETED' (CANCELLED sales have no stock/cash effect;
-- RETURNED sales are reversed by RETURN_IN inventory movements).
CREATE TABLE sales (
  sale_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(shop_id),
  customer_id TEXT, sale_date DATE NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('COMPLETED','CANCELLED','RETURNED')),
  gross_amount_paise BIGINT NOT NULL, discount_amount_paise BIGINT NOT NULL,
  net_amount_paise BIGINT NOT NULL, cost_amount_paise BIGINT NOT NULL, gross_profit_paise BIGINT NOT NULL,
  payment_method TEXT NOT NULL, amount_paid_paise BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT REFERENCES users(user_id),
  UNIQUE(shop_id, sale_id),
  FOREIGN KEY(shop_id, customer_id) REFERENCES customers(shop_id, customer_id)
);
CREATE TABLE sale_items (
  sale_item_id TEXT PRIMARY KEY, sale_id TEXT NOT NULL, shop_id TEXT NOT NULL, product_id TEXT NOT NULL,
  quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0), unit TEXT NOT NULL,
  unit_selling_price_paise BIGINT NOT NULL, unit_cost_at_sale_paise BIGINT NOT NULL,
  gross_amount_paise BIGINT NOT NULL, discount_amount_paise BIGINT NOT NULL,
  net_amount_paise BIGINT NOT NULL, cost_amount_paise BIGINT NOT NULL,
  FOREIGN KEY(shop_id, sale_id) REFERENCES sales(shop_id, sale_id),
  FOREIGN KEY(shop_id, product_id) REFERENCES products(shop_id, product_id)
);
CREATE TABLE purchases (
  purchase_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(shop_id),
  supplier_id TEXT NOT NULL, purchase_date DATE NOT NULL, status TEXT NOT NULL,
  total_amount_paise BIGINT NOT NULL, amount_paid_paise BIGINT NOT NULL, amount_due_paise BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT REFERENCES users(user_id),
  UNIQUE(shop_id, purchase_id),
  FOREIGN KEY(shop_id, supplier_id) REFERENCES suppliers(shop_id, supplier_id)
);
CREATE TABLE purchase_items (
  purchase_item_id TEXT PRIMARY KEY, purchase_id TEXT NOT NULL, shop_id TEXT NOT NULL, product_id TEXT NOT NULL,
  quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0), unit TEXT NOT NULL,
  unit_cost_paise BIGINT NOT NULL, line_total_paise BIGINT NOT NULL,
  FOREIGN KEY(shop_id, purchase_id) REFERENCES purchases(shop_id, purchase_id),
  FOREIGN KEY(shop_id, product_id) REFERENCES products(shop_id, product_id)
);
CREATE TABLE audio_recordings (
  recording_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(shop_id),
  recording_type TEXT NOT NULL CHECK(recording_type IN ('VOICE_NOTE','CALL_RECORDING')),
  file_name TEXT NOT NULL,
  content_hash TEXT NOT NULL,                    -- sha256 of the audio; blocks double-processing
  duration_seconds INTEGER, language TEXT,
  transcript TEXT, transcript_provider TEXT,
  consent_confirmed BOOLEAN NOT NULL DEFAULT FALSE,  -- required before a CALL_RECORDING is processed
  status TEXT NOT NULL CHECK(status IN ('UPLOADED','PROCESSED','NEEDS_REVIEW','FAILED')),
  uploaded_by TEXT REFERENCES users(user_id), uploaded_at TIMESTAMP NOT NULL,
  retention_until DATE,
  UNIQUE(shop_id, content_hash),
  UNIQUE(shop_id, recording_id)
);
-- Credit ledger. Balance = sum over status = 'RECORDED' rows only:
--   + CREDIT_ISSUED, - REPAYMENT, +/- ADJUSTMENT per adjustment_direction.
-- PENDING_CONFIRMATION rows are drafts awaiting the shopkeeper and never count.
-- Corrections are new ADJUSTMENT rows (reverses_credit_txn_id); rows are never edited or deleted.
-- sale_id is NULL for standalone payments / credit given without an itemised sale.
CREATE TABLE credit_transactions (
  credit_txn_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL, customer_id TEXT NOT NULL, sale_id TEXT,
  transaction_type TEXT NOT NULL CHECK(transaction_type IN ('CREDIT_ISSUED','REPAYMENT','ADJUSTMENT')),
  amount_paise BIGINT NOT NULL CHECK(amount_paise >= 0), transaction_date TIMESTAMP NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('RECORDED','PENDING_CONFIRMATION')),
  due_date DATE,                                  -- set on CREDIT_ISSUED
  adjustment_direction TEXT CHECK(adjustment_direction IN ('INCREASE','DECREASE')),
  reverses_credit_txn_id TEXT REFERENCES credit_transactions(credit_txn_id),
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by TEXT REFERENCES users(user_id),
  CHECK((transaction_type = 'ADJUSTMENT') = (adjustment_direction IS NOT NULL)),
  FOREIGN KEY(shop_id, customer_id) REFERENCES customers(shop_id, customer_id),
  FOREIGN KEY(shop_id, sale_id) REFERENCES sales(shop_id, sale_id)
);
CREATE TABLE payment_reminders (
  reminder_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL, customer_id TEXT NOT NULL,
  amount_due_paise BIGINT NOT NULL CHECK(amount_due_paise >= 0),
  channel TEXT NOT NULL CHECK(channel IN ('WHATSAPP','SMS')),
  message_text TEXT NOT NULL,
  status TEXT NOT NULL CHECK(status IN ('DRAFT','SENT','FAILED')),
  created_at TIMESTAMP NOT NULL, sent_at TIMESTAMP,
  FOREIGN KEY(shop_id, customer_id) REFERENCES customers(shop_id, customer_id)
);
-- What was extracted from a recording. Nothing here is a ledger entry until the shopkeeper
-- confirms it; a PAYMENT_COMMITMENT (a promise) never becomes a payment.
CREATE TABLE extracted_drafts (
  draft_id TEXT PRIMARY KEY, recording_id TEXT NOT NULL, shop_id TEXT NOT NULL,
  draft_type TEXT NOT NULL CHECK(draft_type IN ('PAYMENT_RECEIVED','CREDIT_GIVEN','PAYMENT_COMMITMENT')),
  customer_id TEXT,                               -- NULL while the match is ambiguous
  candidate_customer_ids_json JSONB,
  stated_amount_paise BIGINT, stated_remaining_balance_paise BIGINT,
  verified_ledger_balance_paise BIGINT,           -- balance recomputed from the ledger, not trusted from audio
  promised_date DATE, confidence NUMERIC(4,2),
  status TEXT NOT NULL CHECK(status IN ('PENDING_REVIEW','NEEDS_CLARIFICATION','CONFIRMED','REJECTED')),
  confirmed_credit_txn_id TEXT REFERENCES credit_transactions(credit_txn_id),
  notes TEXT, created_at TIMESTAMP NOT NULL, reviewed_at TIMESTAMP,
  FOREIGN KEY(shop_id, recording_id) REFERENCES audio_recordings(shop_id, recording_id),
  FOREIGN KEY(shop_id, customer_id) REFERENCES customers(shop_id, customer_id)
);
-- Opening cash lives on shops.opening_cash_paise; it is not an expense.
CREATE TABLE expenses (
  expense_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(shop_id),
  expense_date DATE NOT NULL, expense_category TEXT NOT NULL, amount_paise BIGINT NOT NULL CHECK(amount_paise >= 0),
  currency CHAR(3) NOT NULL DEFAULT 'INR', payment_method TEXT NOT NULL, description TEXT, recorded_by TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Ordered by (movement_date, movement_id); stock_balance_after replays from products.opening_stock.
CREATE TABLE inventory_movements (
  movement_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL, product_id TEXT NOT NULL, movement_date DATE NOT NULL,
  movement_type TEXT NOT NULL CHECK(movement_type IN ('PURCHASE_IN','SALE_OUT','RETURN_IN','ADJUSTMENT','WASTAGE','MANUAL_IN')),
  quantity_change NUMERIC(14,3) NOT NULL, unit TEXT NOT NULL,
  stock_balance_after NUMERIC(14,3) NOT NULL, source_type TEXT NOT NULL, source_id TEXT NOT NULL, notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by TEXT REFERENCES users(user_id),
  FOREIGN KEY(shop_id, product_id) REFERENCES products(shop_id, product_id)
);
CREATE TABLE orders (
  order_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(shop_id), customer_id TEXT,
  created_at TIMESTAMP NOT NULL, status TEXT NOT NULL CHECK(status IN ('DRAFT','PENDING_CONFIRMATION','CONFIRMED','COMPLETED','CANCELLED')),
  source TEXT NOT NULL, current_version INTEGER NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by TEXT REFERENCES users(user_id),
  UNIQUE(shop_id, order_id),
  FOREIGN KEY(shop_id, customer_id) REFERENCES customers(shop_id, customer_id)
);
CREATE TABLE order_items (
  order_item_id TEXT PRIMARY KEY, order_id TEXT NOT NULL, shop_id TEXT NOT NULL, product_id TEXT NOT NULL,
  quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0), unit TEXT NOT NULL, status TEXT NOT NULL,
  FOREIGN KEY(shop_id, order_id) REFERENCES orders(shop_id, order_id),
  FOREIGN KEY(shop_id, product_id) REFERENCES products(shop_id, product_id)
);
CREATE TABLE order_edit_history (
  edit_id TEXT PRIMARY KEY, order_id TEXT NOT NULL, shop_id TEXT NOT NULL, version INTEGER NOT NULL,
  field_name TEXT NOT NULL, old_value TEXT, new_value TEXT, correction_transcript TEXT, reason TEXT, created_at TIMESTAMP NOT NULL,
  FOREIGN KEY(shop_id, order_id) REFERENCES orders(shop_id, order_id)
);
CREATE TABLE voice_utterances (
  utterance_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(shop_id), raw_transcript TEXT NOT NULL,
  language TEXT NOT NULL, stt_provider TEXT NOT NULL, structured_result_json JSONB NOT NULL,
  matched_product_id TEXT, match_method TEXT NOT NULL, confirmation_status TEXT NOT NULL, created_at TIMESTAMP NOT NULL,
  FOREIGN KEY(shop_id, matched_product_id) REFERENCES products(shop_id, product_id)
);
-- Append-only trail of who changed what. Application code inserts; never update or delete.
CREATE TABLE audit_log (
  audit_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL REFERENCES shops(shop_id),
  actor_user_id TEXT REFERENCES users(user_id),
  entity_type TEXT NOT NULL, entity_id TEXT NOT NULL,
  action TEXT NOT NULL,                           -- CREATE, CONFIRM, REVERSE, STATUS_CHANGE, ...
  old_value_json JSONB, new_value_json JSONB,
  source TEXT NOT NULL CHECK(source IN ('UI','VOICE','AUDIO','IMPORT')),
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_products_shop ON products(shop_id);
CREATE INDEX idx_sales_shop_date ON sales(shop_id, sale_date);
CREATE INDEX idx_credit_customer ON credit_transactions(shop_id, customer_id, transaction_date);
CREATE INDEX idx_credit_due ON credit_transactions(shop_id, due_date) WHERE status = 'RECORDED';
CREATE INDEX idx_movements_product_date ON inventory_movements(shop_id, product_id, movement_date);
CREATE INDEX idx_voice_shop_date ON voice_utterances(shop_id, created_at);
CREATE INDEX idx_reminders_customer ON payment_reminders(shop_id, customer_id);
CREATE INDEX idx_drafts_status ON extracted_drafts(shop_id, status);
CREATE INDEX idx_audit_entity ON audit_log(shop_id, entity_type, entity_id);

CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END $$ LANGUAGE plpgsql;
DO $$
DECLARE t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['shops','users','suppliers','products','customers','sales','purchases',
                           'credit_transactions','expenses','orders'] LOOP
    EXECUTE format('CREATE TRIGGER trg_%s_updated BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t, t);
  END LOOP;
END $$;
