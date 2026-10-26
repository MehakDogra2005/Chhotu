"""One-off: add two orders per shop that have been placed with the supplier but not received yet
(status ORDERED), for products that are out of stock or low. They are what the Inventory page
shows as "On Order". Nothing arrives, so no inventory movement is created. Run once.
"""
import json
import sys
from pathlib import Path

import pandas as pd

DB = Path(__file__).resolve().parent.parent / "db"
load = lambda n: pd.read_csv(DB / f"{n}.csv", dtype=str, keep_default_na=False)  # noqa: E731

purchases, items = load("purchases"), load("purchase_items")
if (purchases.status == "ORDERED").any():
    sys.exit("already added")

products, moves = load("products"), load("inventory_movements")
stock = products.set_index("product_id").opening_stock.astype(float)
stock = stock.add(moves.assign(q=moves.quantity_change.astype(float)).groupby("product_id").q.sum(), fill_value=0)
products["stock"] = products.product_id.map(stock)
low = products[products.stock <= products.reorder_level.astype(float)].sort_values("stock")

new_purchases, new_items = [], []
for shop_id, shop_low in low.groupby("shop_id"):
    for _, p in shop_low.head(2).iterrows():  # the two most urgent products
        qty = int(float(p.reorder_level)) * 2
        cost = int(p.unit_purchase_price_paise)
        total = qty * cost
        n = len(purchases) + len(new_purchases) + 1
        new_purchases.append({
            "purchase_id": f"PUR-{n:05d}", "shop_id": shop_id, "supplier_id": p.supplier_id,
            "purchase_date": "2026-10-17", "status": "ORDERED",
            "total_amount_paise": total, "amount_paid_paise": 0, "amount_due_paise": total})
        new_items.append({
            "purchase_item_id": f"POI-{len(items) + len(new_items) + 1:06d}", "purchase_id": f"PUR-{n:05d}",
            "shop_id": shop_id, "product_id": p.product_id, "quantity": qty, "unit": p.unit,
            "unit_cost_paise": cost, "line_total_paise": total})

pd.concat([purchases, pd.DataFrame(new_purchases)]).to_csv(DB / "purchases.csv", index=False)
pd.concat([items, pd.DataFrame(new_items)]).to_csv(DB / "purchase_items.csv", index=False)

counts = json.loads((DB / "validation_counts.json").read_text())
counts["purchases"] += len(new_purchases)
counts["purchase_items"] += len(new_items)
(DB / "validation_counts.json").write_text(json.dumps(counts, indent=2) + "\n")
print("added", len(new_purchases), "pending orders")
