package com.dukaansaathi.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dukaansaathi.inventory.InventoryResponse.Counts;
import com.dukaansaathi.inventory.InventoryResponse.Item;

/** All the stock SQL. Every query filters on shop_id, so a shop can only ever see its own products. */
@Repository
public class InventoryRepository {
    /** The tabs on the Inventory page, and the SQL each one adds. Fixed text, never built from user input. */
    public static final Map<String, String> FILTERS = Map.of(
            "all", "",
            "low", " AND stock > 0 AND stock <= reorder_level",
            "out", " AND stock <= 0",
            "order", " AND on_order = 1");

    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Counts counts(String shopId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(CASE WHEN stock > 0 AND stock <= reorder_level THEN 1 ELSE 0 END), 0) AS low,
                       COALESCE(SUM(CASE WHEN stock <= 0 THEN 1 ELSE 0 END), 0) AS out_of_stock,
                       COALESCE(SUM(on_order), 0) AS on_order,
                       COALESCE(SUM(CASE WHEN stock > 0 THEN stock * unit_purchase_price_paise ELSE 0 END), 0) AS stock_value
                FROM inventory_overview
                WHERE shop_id = ?
                """,
                (rs, i) -> new Counts(rs.getInt("total"), rs.getInt("low"), rs.getInt("out_of_stock"),
                        rs.getInt("on_order"), Math.round(rs.getDouble("stock_value"))),
                shopId);
    }

    /** Products for one tab, matching the search text in the name or category, most recently changed first. */
    public List<Item> items(String shopId, String filter, String search, int limit) {
        String like = "%" + search.trim().toLowerCase() + "%";
        return jdbc.query("""
                SELECT product_name, category, stock, unit, reorder_level, last_updated, on_order
                FROM inventory_overview
                WHERE shop_id = ?
                """ + FILTERS.get(filter) + """
                 AND (LOWER(product_name) LIKE ? OR LOWER(category) LIKE ?)
                ORDER BY last_updated DESC NULLS LAST, product_name
                LIMIT ?
                """,
                (rs, i) -> new Item(rs.getString("product_name"), rs.getString("category"), rs.getDouble("stock"),
                        rs.getString("unit"), rs.getDouble("reorder_level"),
                        StockItem.statusOf(rs.getDouble("stock"), rs.getDouble("reorder_level")),
                        rs.getObject("last_updated", LocalDate.class), rs.getInt("on_order") == 1),
                shopId, like, like, limit);
    }

    /** Products closest to (or below) their reorder level first. */
    public List<StockItem> lowestStock(String shopId, boolean onlyLow, int limit) {
        String where = onlyLow ? " AND stock <= reorder_level" : "";
        return jdbc.query("SELECT product_name, unit, stock, reorder_level FROM product_stock WHERE shop_id = ?" + where
                        + " ORDER BY stock - reorder_level LIMIT ?",
                (rs, i) -> StockItem.of(rs.getString("product_name"), rs.getDouble("stock"), rs.getString("unit"),
                        rs.getDouble("reorder_level")),
                shopId, limit);
    }

    public int lowStockCount(String shopId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_stock WHERE shop_id = ? AND stock <= reorder_level", Integer.class, shopId);
        return count == null ? 0 : count;
    }
}
