package com.dukaansaathi.restock;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Restocking SQL: what to order, the purchase orders, and receiving them. Every query filters on shop_id. */
@Repository
public class RestockRepository {
    /** A product that may need ordering, with what it costs and who sells it. */
    public record Candidate(String productId, String name, String unit, double stock, double reorderLevel,
                            long unitCostPaise, boolean onOrder, String supplierId, String supplierName) {}

    public record PendingOrder(String purchaseId, String supplierName) {}

    public record OrderLine(String lineId, String productId, double quantity, String unit) {}

    private static final String CANDIDATES = """
            SELECT o.product_id, o.product_name, o.unit, o.stock, o.reorder_level, o.unit_purchase_price_paise, o.on_order,
                   p.supplier_id, s.supplier_name
            FROM inventory_overview o
            JOIN products p ON p.product_id = o.product_id
            LEFT JOIN suppliers s ON s.supplier_id = p.supplier_id
            WHERE o.shop_id = ?
            """;

    private final JdbcTemplate jdbc;

    public RestockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Low or out of stock, and not already waiting for a delivery. */
    public List<Candidate> needed(String shopId) {
        return jdbc.query(CANDIDATES + " AND o.stock <= o.reorder_level AND o.on_order = 0 ORDER BY o.stock - o.reorder_level",
                (rs, i) -> toCandidate(rs), shopId);
    }

    /** These products, whatever their stock. */
    public List<Candidate> byIds(String shopId, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        String marks = String.join(", ", Collections.nCopies(ids.size(), "?")); // ?, ?, ? (one per id)
        Object[] args = new Object[ids.size() + 1];
        args[0] = shopId;
        for (int i = 0; i < ids.size(); i++) args[i + 1] = ids.get(i);
        return jdbc.query(CANDIDATES + " AND o.product_id IN (" + marks + ") ORDER BY o.product_name", (rs, i) -> toCandidate(rs), args);
    }

    public String insertOrder(String shopId, String supplierId, LocalDate date, long totalPaise) {
        String id = "PUR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO purchases (purchase_id, shop_id, supplier_id, purchase_date, status, total_amount_paise,
                                       amount_paid_paise, amount_due_paise)
                VALUES (?, ?, ?, ?, 'ORDERED', ?, 0, ?)
                """, id, shopId, supplierId, date, totalPaise, totalPaise);
        return id;
    }

    public void insertLine(String shopId, String purchaseId, String productId, double quantity, String unit, long unitCostPaise) {
        jdbc.update("""
                INSERT INTO purchase_items (purchase_item_id, purchase_id, shop_id, product_id, quantity, unit, unit_cost_paise, line_total_paise)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, "POI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), purchaseId, shopId, productId,
                quantity, unit, unitCostPaise, Math.round(quantity * unitCostPaise));
    }

    /** Orders placed but not received yet that contain this product. */
    public List<PendingOrder> pendingFor(String shopId, String productId) {
        return jdbc.query("""
                SELECT DISTINCT pu.purchase_id, COALESCE(s.supplier_name, 'your supplier') AS supplier_name
                FROM purchases pu
                JOIN purchase_items pi ON pi.purchase_id = pu.purchase_id
                LEFT JOIN suppliers s ON s.supplier_id = pu.supplier_id
                WHERE pu.shop_id = ? AND pu.status = 'ORDERED' AND pi.product_id = ?
                """,
                (rs, i) -> new PendingOrder(rs.getString("purchase_id"), rs.getString("supplier_name")), shopId, productId);
    }

    public List<OrderLine> lines(String purchaseId) {
        return jdbc.query("SELECT purchase_item_id, product_id, quantity, unit FROM purchase_items WHERE purchase_id = ?",
                (rs, i) -> new OrderLine(rs.getString("purchase_item_id"), rs.getString("product_id"), rs.getDouble("quantity"), rs.getString("unit")),
                purchaseId);
    }

    public void markReceived(String shopId, String purchaseId) {
        jdbc.update("UPDATE purchases SET status = 'RECEIVED' WHERE shop_id = ? AND purchase_id = ?", shopId, purchaseId);
    }

    private static Candidate toCandidate(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Candidate(rs.getString("product_id"), rs.getString("product_name"), rs.getString("unit"), rs.getDouble("stock"),
                rs.getDouble("reorder_level"), rs.getLong("unit_purchase_price_paise"), rs.getInt("on_order") == 1,
                rs.getString("supplier_id"), rs.getString("supplier_name"));
    }
}
