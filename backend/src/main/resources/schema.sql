-- Tables the app reads and writes. Same names and columns as db/schema.sql, in plain SQL
-- that runs on H2 (local) and PostgreSQL. Money is whole paise (1 rupee = 100 paise).
-- Every table that holds shop data has shop_id, and every query filters on it.

CREATE TABLE IF NOT EXISTS shops (
  shop_id VARCHAR(40) PRIMARY KEY,
  shop_name VARCHAR(200) NOT NULL,
  business_type VARCHAR(100),
  city VARCHAR(100),
  language VARCHAR(30),
  currency CHAR(3) DEFAULT 'INR' NOT NULL,
  is_active BOOLEAN DEFAULT TRUE NOT NULL,
  opening_cash_paise BIGINT DEFAULT 0 NOT NULL,
  opening_cash_date DATE
);

CREATE TABLE IF NOT EXISTS users (
  user_id VARCHAR(40) PRIMARY KEY,
  phone VARCHAR(15) UNIQUE,                -- people who sign in with Google or email may have no phone
  email VARCHAR(200) UNIQUE,
  display_name VARCHAR(200),
  is_active BOOLEAN DEFAULT TRUE NOT NULL
);
-- For databases created before phone became optional
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;

CREATE TABLE IF NOT EXISTS user_shops (
  user_id VARCHAR(40) NOT NULL REFERENCES users(user_id),
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  role VARCHAR(20) NOT NULL,
  PRIMARY KEY (user_id, shop_id)
);

-- Preferences the shopkeeper can change on the Settings page. A shop with no row here uses the defaults.
CREATE TABLE IF NOT EXISTS shop_settings (
  shop_id VARCHAR(40) PRIMARY KEY REFERENCES shops(shop_id),
  voice_mode VARCHAR(20) DEFAULT 'PUSH_TO_TALK' NOT NULL,   -- PUSH_TO_TALK or HANDS_FREE
  voice_replies BOOLEAN DEFAULT TRUE NOT NULL,
  low_stock_alerts BOOLEAN DEFAULT TRUE NOT NULL,
  payment_reminders BOOLEAN DEFAULT TRUE NOT NULL
);

CREATE TABLE IF NOT EXISTS suppliers (
  supplier_id VARCHAR(40) PRIMARY KEY,
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  supplier_name VARCHAR(200) NOT NULL,
  contact_phone_masked VARCHAR(20),
  payment_terms_days INTEGER DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS customers (
  customer_id VARCHAR(40) PRIMARY KEY,
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  customer_name VARCHAR(200) NOT NULL,
  phone_masked VARCHAR(20),
  customer_type VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS products (
  product_id VARCHAR(40) PRIMARY KEY,
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  product_name VARCHAR(200) NOT NULL,
  category VARCHAR(100) NOT NULL,
  subcategory VARCHAR(100),
  brand VARCHAR(100),
  variant VARCHAR(100),
  unit VARCHAR(30) NOT NULL,
  unit_purchase_price_paise BIGINT NOT NULL,
  unit_selling_price_paise BIGINT NOT NULL,
  opening_stock DECIMAL(14,3) DEFAULT 0 NOT NULL,
  reorder_level DECIMAL(14,3) DEFAULT 0 NOT NULL,
  supplier_id VARCHAR(40),
  is_active BOOLEAN DEFAULT TRUE NOT NULL
);

CREATE TABLE IF NOT EXISTS purchases (
  purchase_id VARCHAR(40) PRIMARY KEY,
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  supplier_id VARCHAR(40) NOT NULL,
  purchase_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL,             -- ORDERED (placed, not received yet) or RECEIVED
  total_amount_paise BIGINT NOT NULL,
  amount_paid_paise BIGINT NOT NULL,
  amount_due_paise BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS purchase_items (
  purchase_item_id VARCHAR(40) PRIMARY KEY,
  purchase_id VARCHAR(40) NOT NULL REFERENCES purchases(purchase_id),
  shop_id VARCHAR(40) NOT NULL,
  product_id VARCHAR(40) NOT NULL REFERENCES products(product_id),
  quantity DECIMAL(14,3) NOT NULL,
  unit VARCHAR(30) NOT NULL,
  unit_cost_paise BIGINT NOT NULL,
  line_total_paise BIGINT NOT NULL
);

-- Other names shopkeepers use for a product ("paani bottle" = Bottled Water). Used to understand voice commands.
CREATE TABLE IF NOT EXISTS product_aliases (
  alias_id VARCHAR(40) PRIMARY KEY,
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  product_id VARCHAR(40) NOT NULL REFERENCES products(product_id),
  alias_text VARCHAR(200) NOT NULL,
  language VARCHAR(20) NOT NULL,
  source VARCHAR(30) NOT NULL
);

CREATE TABLE IF NOT EXISTS sales (
  sale_id VARCHAR(40) PRIMARY KEY,
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  customer_id VARCHAR(40),
  sale_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL,             -- COMPLETED, CANCELLED, RETURNED: reports count COMPLETED only
  gross_amount_paise BIGINT NOT NULL,
  discount_amount_paise BIGINT NOT NULL,
  net_amount_paise BIGINT NOT NULL,
  cost_amount_paise BIGINT NOT NULL,
  gross_profit_paise BIGINT NOT NULL,
  payment_method VARCHAR(30) NOT NULL,
  amount_paid_paise BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS sale_items (
  sale_item_id VARCHAR(40) PRIMARY KEY,
  sale_id VARCHAR(40) NOT NULL REFERENCES sales(sale_id),
  shop_id VARCHAR(40) NOT NULL,
  product_id VARCHAR(40) NOT NULL REFERENCES products(product_id),
  quantity DECIMAL(14,3) NOT NULL,
  unit VARCHAR(30) NOT NULL,
  unit_selling_price_paise BIGINT NOT NULL,
  unit_cost_at_sale_paise BIGINT NOT NULL,
  gross_amount_paise BIGINT NOT NULL,
  discount_amount_paise BIGINT NOT NULL,
  net_amount_paise BIGINT NOT NULL,
  cost_amount_paise BIGINT NOT NULL
);

-- Credit ledger: rows are only ever added. A customer's balance is the sum of RECORDED rows
-- (see the customer_balance view); PENDING_CONFIRMATION rows do not count yet.
CREATE TABLE IF NOT EXISTS credit_transactions (
  credit_txn_id VARCHAR(40) PRIMARY KEY,
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  customer_id VARCHAR(40) NOT NULL REFERENCES customers(customer_id),
  sale_id VARCHAR(40),
  transaction_type VARCHAR(20) NOT NULL,   -- CREDIT_ISSUED, REPAYMENT, ADJUSTMENT
  amount_paise BIGINT NOT NULL,
  transaction_date DATE NOT NULL,
  status VARCHAR(30) NOT NULL,             -- RECORDED, PENDING_CONFIRMATION
  due_date DATE,
  adjustment_direction VARCHAR(10),        -- INCREASE or DECREASE, only for ADJUSTMENT
  reverses_credit_txn_id VARCHAR(40),
  note VARCHAR(500)
);

-- Stock is never edited; every change is a movement (sale, purchase, return, adjustment...).
CREATE TABLE IF NOT EXISTS inventory_movements (
  movement_id VARCHAR(40) PRIMARY KEY,
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  product_id VARCHAR(40) NOT NULL REFERENCES products(product_id),
  movement_date DATE NOT NULL,
  movement_type VARCHAR(30) NOT NULL,
  quantity_change DECIMAL(14,3) NOT NULL,
  unit VARCHAR(30) NOT NULL,
  stock_balance_after DECIMAL(14,3) NOT NULL,
  source_type VARCHAR(30) NOT NULL,
  source_id VARCHAR(40) NOT NULL,
  notes VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS voice_utterances (
  utterance_id VARCHAR(40) PRIMARY KEY,
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  raw_transcript VARCHAR(1000) NOT NULL,
  language VARCHAR(20) NOT NULL,
  stt_provider VARCHAR(50) NOT NULL,
  structured_result_json VARCHAR(2000) NOT NULL,
  matched_product_id VARCHAR(40),
  match_method VARCHAR(30) NOT NULL,
  confirmation_status VARCHAR(30) NOT NULL,
  created_at TIMESTAMP NOT NULL
);
-- For databases created when this column only held the date
ALTER TABLE voice_utterances ALTER COLUMN created_at SET DATA TYPE TIMESTAMP;

CREATE TABLE IF NOT EXISTS audio_recordings (
  recording_id VARCHAR(40) PRIMARY KEY,
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  recording_type VARCHAR(20) NOT NULL,     -- VOICE_NOTE or CALL_RECORDING
  file_name VARCHAR(200) NOT NULL,
  content_hash VARCHAR(100) NOT NULL,
  duration_seconds INTEGER,
  language VARCHAR(20),
  transcript VARCHAR(4000),
  transcript_provider VARCHAR(50),
  consent_confirmed BOOLEAN DEFAULT FALSE NOT NULL,
  status VARCHAR(20) NOT NULL,
  uploaded_by VARCHAR(40),
  uploaded_at TIMESTAMP NOT NULL,
  retention_until DATE,
  UNIQUE (shop_id, content_hash)
);

-- Who changed what, and when. Rows are only ever added.
CREATE TABLE IF NOT EXISTS audit_log (
  audit_id VARCHAR(40) PRIMARY KEY,
  shop_id VARCHAR(40) NOT NULL REFERENCES shops(shop_id),
  actor_user_id VARCHAR(40),
  entity_type VARCHAR(40) NOT NULL,
  entity_id VARCHAR(40) NOT NULL,
  action VARCHAR(30) NOT NULL,
  old_value_json VARCHAR(2000),
  new_value_json VARCHAR(2000),
  source VARCHAR(10) NOT NULL,             -- UI, VOICE, AUDIO or IMPORT
  created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sales_shop_date ON sales(shop_id, sale_date);
CREATE INDEX IF NOT EXISTS idx_sale_items_sale ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_credit_customer ON credit_transactions(shop_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_movements_product ON inventory_movements(shop_id, product_id);
CREATE INDEX IF NOT EXISTS idx_utterances_shop_date ON voice_utterances(shop_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_log(shop_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_purchase_items_product ON purchase_items(product_id);

-- Current stock of each product = opening stock + all its movements.
CREATE OR REPLACE VIEW product_stock AS
SELECT p.product_id, p.shop_id, p.product_name, p.unit, p.reorder_level,
       p.opening_stock + COALESCE(SUM(m.quantity_change), 0) AS stock
FROM products p
LEFT JOIN inventory_movements m ON m.product_id = p.product_id
WHERE p.is_active = TRUE
GROUP BY p.product_id, p.shop_id, p.product_name, p.unit, p.reorder_level, p.opening_stock;

-- One row per product for the Inventory page: stock, price, last stock change, and whether it is on order.
CREATE OR REPLACE VIEW inventory_overview AS
SELECT s.product_id, s.shop_id, s.product_name, p.category, s.unit, s.reorder_level, s.stock,
       p.unit_purchase_price_paise,
       (SELECT MAX(m.movement_date) FROM inventory_movements m WHERE m.product_id = s.product_id) AS last_updated,
       CASE WHEN EXISTS (SELECT 1 FROM purchase_items pi JOIN purchases pu ON pu.purchase_id = pi.purchase_id
                         WHERE pi.product_id = s.product_id AND pu.status = 'ORDERED')
            THEN 1 ELSE 0 END AS on_order
FROM product_stock s
JOIN products p ON p.product_id = s.product_id;

-- What each customer owes = credit given - repayments +/- adjustments (RECORDED rows only).
CREATE OR REPLACE VIEW customer_balance AS
SELECT c.customer_id, c.shop_id, c.customer_name,
       COALESCE(SUM(CASE WHEN t.transaction_type = 'CREDIT_ISSUED' THEN t.amount_paise ELSE 0 END), 0) AS total_credit_paise,
       COALESCE(SUM(CASE WHEN t.transaction_type = 'CREDIT_ISSUED' THEN t.amount_paise
                         WHEN t.transaction_type = 'REPAYMENT' THEN -t.amount_paise
                         WHEN t.adjustment_direction = 'INCREASE' THEN t.amount_paise
                         ELSE -t.amount_paise END), 0) AS balance_paise
FROM customers c
LEFT JOIN credit_transactions t ON t.customer_id = c.customer_id AND t.status = 'RECORDED'
GROUP BY c.customer_id, c.shop_id, c.customer_name;
