# Dukaan Saathi interconnected demo data

This package contains synthetic data for six retail shop types: general retail, stationery, grocery, edible-oil retail, convenience store, and household/general retail.

## Files
- shops.csv
- products.csv
- product_aliases.csv
- suppliers.csv
- customers.csv
- sales.csv and sale_items.csv
- purchases.csv and purchase_items.csv
- credit_transactions.csv
- expenses.csv
- inventory_movements.csv
- voice_utterances.csv
- orders.csv, order_items.csv, order_edit_history.csv
- users.csv, user_shops.csv (OTP login: phone is the identity)
- audio_recordings.csv, extracted_drafts.csv (voice notes / call recordings and what was extracted)
- payment_reminders.csv, audit_log.csv
- schema.sql (PostgreSQL schema with composite shop-scoped foreign keys)

## Important conventions
- All money columns end in `_paise` and contain integer paise (₹1 = 100 paise).
- Every quantity column has a corresponding `unit` column.
- `credit_transactions.csv` has a unique `credit_txn_id`.
- Product aliases map to actual product IDs in that shop's catalogue.
- `shop_id` is included for tenant scoping. In production, backend code MUST derive it from the authenticated session/token, never trust it from request JSON or LLM output.
- The schema uses composite foreign keys for shop-scoped relationships. The app should also enforce tenant scoping on every read/update/delete and use least-privilege database access.
- Data is synthetic; masked phone fields are fake placeholders, not real contact details.
- Import parent tables before child tables. For a live system, prefer PostgreSQL as the source of truth; CSVs are seed/import files only.

- Sales `status` is COMPLETED, CANCELLED or RETURNED. Reports must count COMPLETED only; returns are reversed by `RETURN_IN` movements.
- `inventory_movements` is ordered by (movement_date, movement_id); `stock_balance_after` replays from `products.opening_stock`.
- Customer balance = sum of `RECORDED` credit rows: +CREDIT_ISSUED, -REPAYMENT, +/-ADJUSTMENT (`adjustment_direction`). `PENDING_CONFIRMATION` rows never count. `sale_id` is NULL for standalone payments. Corrections are new ADJUSTMENT rows with `reverses_credit_txn_id`.
- Extracted drafts are not ledger entries until confirmed. A `PAYMENT_COMMITMENT` (a promise) never becomes a payment, and `verified_ledger_balance_paise` is recomputed rather than trusted from audio.
- Opening cash is `shops.opening_cash_paise`. created_at/updated_at/created_by have defaults in the schema and are absent from the CSVs, so import with explicit column lists.
- `scripts/fix_db.py` produced these changes from the first version of the data. Do not re-run it on migrated data.

## Import order
shops -> users -> user_shops -> suppliers -> products -> customers -> product_aliases -> sales -> sale_items -> purchases -> purchase_items -> credit_transactions -> expenses -> inventory_movements -> orders -> order_items -> order_edit_history -> voice_utterances -> audio_recordings -> extracted_drafts -> payment_reminders -> audit_log

## Voice product matching
1. Normalize transcript phrase and search `product_aliases` scoped to authenticated `shop_id`.
2. If no alias match, ask the LLM to select from that shop's catalogue and return an existing `product_id` plus confidence; do not accept arbitrary product names/IDs.
3. On high-confidence matches, add a normalized alias only after validation; enforce unique `(shop_id, normalized_alias)` and handle collisions.
4. On low confidence or ambiguous match, ask for confirmation.
5. Keep the raw transcript and structured result in `voice_utterances`; log order corrections in `order_edit_history`.

## Note
This is a demo seed dataset and schema, not production-ready application code. Before production, add migrations, stronger role/permission rules, authentication, audit policies, and tests for tenant isolation.
