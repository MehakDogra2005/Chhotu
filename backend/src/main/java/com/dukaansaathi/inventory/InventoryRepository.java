package com.dukaansaathi.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    /** A product and how much of it is in stock right now. */
    public record ProductStock(String productId, String name, String unit, double stock, double reorderLevel) {}

    /** Everything the Edit Item form needs. */
    public record ProductDetails(String productId, String name, String category, String unit, long purchasePricePaise,
                                 long sellingPricePaise, double reorderLevel, double openingStock) {}

    /** One line of a product's stock history. quantityChange is negative when stock went out. */
    public record Movement(LocalDate date, String type, double quantityChange, String unit, double balanceAfter, String note) {}

    /** One product in the stock report. valuePaise is stock x purchase price. */
    public record ReportRow(String name, String category, double stock, String unit, double reorderLevel, String status, long valuePaise) {}

    private static final String PRODUCT_SELECT =
            "SELECT product_id, product_name, unit, stock, reorder_level FROM product_stock WHERE shop_id = ?";

    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ the Inventory page

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
                SELECT product_id, product_name, category, stock, unit, reorder_level, last_updated, on_order
                FROM inventory_overview
                WHERE shop_id = ?
                """ + FILTERS.get(filter) + """
                 AND (LOWER(product_name) LIKE ? OR LOWER(category) LIKE ?)
                ORDER BY last_updated DESC NULLS LAST, product_name
                LIMIT ?
                """,
                (rs, i) -> new Item(rs.getString("product_id"), rs.getString("product_name"), rs.getString("category"),
                        rs.getDouble("stock"), rs.getString("unit"), rs.getDouble("reorder_level"),
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

    // ------------------------------------------------------------------ one product

    public List<ProductStock> products(String shopId) {
        return jdbc.query(PRODUCT_SELECT + " ORDER BY product_name", (rs, i) -> toProductStock(rs), shopId);
    }

    public Optional<ProductStock> findProduct(String shopId, String productId) {
        return jdbc.query(PRODUCT_SELECT + " AND product_id = ?", (rs, i) -> toProductStock(rs), shopId, productId).stream().findFirst();
    }

    public Optional<ProductDetails> details(String shopId, String productId) {
        return jdbc.query("""
                SELECT product_id, product_name, category, unit, unit_purchase_price_paise, unit_selling_price_paise,
                       reorder_level, opening_stock
                FROM products
                WHERE shop_id = ? AND product_id = ? AND is_active = TRUE
                """,
                (rs, i) -> new ProductDetails(rs.getString("product_id"), rs.getString("product_name"), rs.getString("category"),
                        rs.getString("unit"), rs.getLong("unit_purchase_price_paise"), rs.getLong("unit_selling_price_paise"),
                        rs.getDouble("reorder_level"), rs.getDouble("opening_stock")),
                shopId, productId).stream().findFirst();
    }

    /** Is there already a live product with this name (ignoring capital letters)? exceptId is the product being edited. */
    public boolean nameTaken(String shopId, String name, String exceptId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM products
                WHERE shop_id = ? AND LOWER(product_name) = LOWER(?) AND is_active = TRUE AND product_id <> ?
                """, Integer.class, shopId, name, exceptId);
        return count != null && count > 0;
    }

    public boolean isOnOrder(String shopId, String productId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_overview WHERE shop_id = ? AND product_id = ? AND on_order = 1",
                Integer.class, shopId, productId);
        return count != null && count > 0;
    }

    public String insertProduct(String shopId, ProductForm f) {
        String id = "PRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO products (product_id, shop_id, product_name, category, unit, unit_purchase_price_paise,
                                      unit_selling_price_paise, opening_stock, reorder_level, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                """, id, shopId, f.name(), f.category(), f.unit(), f.purchasePricePaise(), f.sellingPricePaise(),
                f.openingStock(), f.reorderLevel());
        return id;
    }

    /** The unit and the opening stock are not edited: stock only changes through movements. */
    public void updateProduct(String shopId, String productId, ProductForm f) {
        jdbc.update("""
                UPDATE products
                SET product_name = ?, category = ?, unit_purchase_price_paise = ?, unit_selling_price_paise = ?, reorder_level = ?
                WHERE shop_id = ? AND product_id = ?
                """, f.name(), f.category(), f.purchasePricePaise(), f.sellingPricePaise(), f.reorderLevel(), shopId, productId);
    }

    /** Archiving hides the product everywhere but keeps its history. */
    public void archive(String shopId, String productId) {
        jdbc.update("UPDATE products SET is_active = FALSE WHERE shop_id = ? AND product_id = ?", shopId, productId);
    }

    // ------------------------------------------------------------------ stock movements and reports

    /** Stock is never edited; a change is a new movement. quantityChange is positive for stock coming in. */
    public void addMovement(String shopId, String productId, LocalDate date, String type, double quantityChange,
                            String unit, double balanceAfter, String sourceType, String sourceId, String notes) {
        jdbc.update("""
                INSERT INTO inventory_movements (movement_id, shop_id, product_id, movement_date, movement_type, quantity_change,
                                                 unit, stock_balance_after, source_type, source_id, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "MOV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), shopId, productId, date, type,
                quantityChange, unit, balanceAfter, sourceType, sourceId, notes);
    }

    /** The latest movements of one product, newest first. */
    public List<Movement> movements(String shopId, String productId, int limit) {
        return jdbc.query("""
                SELECT movement_date, movement_type, quantity_change, unit, stock_balance_after, notes
                FROM inventory_movements
                WHERE shop_id = ? AND product_id = ?
                ORDER BY movement_date DESC, movement_id DESC
                LIMIT ?
                """,
                (rs, i) -> new Movement(rs.getObject("movement_date", LocalDate.class), rs.getString("movement_type"),
                        rs.getDouble("quantity_change"), rs.getString("unit"), rs.getDouble("stock_balance_after"), rs.getString("notes")),
                shopId, productId, limit);
    }

    public List<ReportRow> reportRows(String shopId) {
        return jdbc.query("""
                SELECT product_name, category, stock, unit, reorder_level, unit_purchase_price_paise
                FROM inventory_overview
                WHERE shop_id = ?
                ORDER BY category, product_name
                """,
                (rs, i) -> new ReportRow(rs.getString("product_name"), rs.getString("category"), rs.getDouble("stock"),
                        rs.getString("unit"), rs.getDouble("reorder_level"),
                        StockItem.statusOf(rs.getDouble("stock"), rs.getDouble("reorder_level")),
                        Math.round(Math.max(rs.getDouble("stock"), 0) * rs.getLong("unit_purchase_price_paise"))),
                shopId);
    }

    private static ProductStock toProductStock(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProductStock(rs.getString("product_id"), rs.getString("product_name"), rs.getString("unit"),
                rs.getDouble("stock"), rs.getDouble("reorder_level"));
    }
}
