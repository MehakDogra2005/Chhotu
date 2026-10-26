"""One-off: make the seed data usable for the dashboard demo (run once).

- users.csv: valid dummy phone numbers (so OTP login works) and real owner names
- inventory_movements.csv: a stock-count correction on 4 products per shop, so each shop
  has products that are out of stock or below their reorder level
"""
import json
import random
import sys
from pathlib import Path

import pandas as pd

DB = Path(__file__).resolve().parent.parent / "db"
rng = random.Random(11)
load = lambda n: pd.read_csv(DB / f"{n}.csv", dtype=str, keep_default_na=False)  # noqa: E731

users = load("users")
if users.phone.iloc[0].startswith("999990000"):
    sys.exit("already prepared")

# ---- users: owner names + dummy phones (OTP is printed to the backend console, never sent as SMS)
owners = ["Rajesh Sharma", "Anita Mehra", "Vikram Singh", "Suresh Iyer", "Kavita Joshi", "Manoj Khanna"]
users["phone"] = [f"999990000{i}" for i in range(1, len(users) + 1)]
users["display_name"] = owners[: len(users)]
users.to_csv(DB / "users.csv", index=False)

# ---- low stock
products, moves, audit = load("products"), load("inventory_movements"), load("audit_log")
stock = products.set_index("product_id").opening_stock.astype(float)
stock = stock.add(moves.assign(q=moves.quantity_change.astype(float)).groupby("product_id").q.sum(), fill_value=0)
owner = {r.shop_id: r.user_id for r in load("user_shops").itertuples()}
next_mov, next_adj, next_aud = len(moves) + 1, 1000, len(audit) + 1
new_moves, new_audit = [], []

for shop_id, shop_products in products.groupby("shop_id"):
    chosen = shop_products.sample(4, random_state=rng.randint(0, 9999))
    for (_, p), share in zip(chosen.iterrows(), [0, 0.25, 0.5, 0.9]):
        target = round(float(p.reorder_level) * share)  # 0 = out of stock, others below the reorder level
        change = target - stock[p.product_id]
        next_adj += 1
        new_moves.append({
            "movement_id": f"MOV-{next_mov:06d}", "shop_id": shop_id, "product_id": p.product_id,
            "movement_date": "2026-10-18", "movement_type": "ADJUSTMENT", "quantity_change": f"{change:g}",
            "unit": p.unit, "stock_balance_after": str(target), "source_type": "STOCK_COUNT",
            "source_id": f"ADJ-{next_adj:04d}", "notes": "Stock count correction"})
        new_audit.append({
            "audit_id": f"AUD-{next_aud:06d}", "shop_id": shop_id, "actor_user_id": owner[shop_id],
            "entity_type": "inventory_movement", "entity_id": f"ADJ-{next_adj:04d}", "action": "CREATE",
            "old_value_json": "", "new_value_json": json.dumps({"type": "ADJUSTMENT", "qty": change, "product_id": p.product_id}),
            "source": "UI", "created_at": f"2026-10-18 {rng.randint(9, 19):02d}:{rng.randint(0, 59):02d}:00"})
        next_mov += 1
        next_aud += 1

pd.concat([moves, pd.DataFrame(new_moves)]).to_csv(DB / "inventory_movements.csv", index=False)
pd.concat([audit, pd.DataFrame(new_audit)]).to_csv(DB / "audit_log.csv", index=False)

counts = json.loads((DB / "validation_counts.json").read_text())
counts["inventory_movements"] += len(new_moves)
counts["audit_log"] += len(new_audit)
(DB / "validation_counts.json").write_text(json.dumps(counts, indent=2) + "\n")
print("added", len(new_moves), "stock corrections")
