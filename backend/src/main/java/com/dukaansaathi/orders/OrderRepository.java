package com.dukaansaathi.orders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Customer orders (what a customer wants to buy). Every query filters on shop_id. An order does not change stock by itself. */
@Repository
public class OrderRepository {
    public record Line(String productId, String name, String unit, double quantity) {}

    public record NewLine(String productId, String unit, double quantity) {}

    public record OrderRow(String orderId, String customerId, String customerName, LocalDateTime createdAt, String status,
                           String source, List<Line> lines) {}

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The newest orders first, each with its lines. */
    public List<OrderRow> recent(String shopId, int limit) {
        record Head(String id, String customerId, String customerName, LocalDateTime at, String status, String source) {}
        List<Head> heads = jdbc.query("""
                SELECT o.order_id, o.customer_id, c.customer_name, o.created_at, o.status, o.source
                FROM orders o
                LEFT JOIN customers c ON c.shop_id = o.shop_id AND c.customer_id = o.customer_id
                WHERE o.shop_id = ?
                ORDER BY o.created_at DESC, o.order_id DESC
                LIMIT ?
                """,
                (rs, i) -> new Head(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4, LocalDateTime.class),
                        rs.getString(5), rs.getString(6)),
                shopId, limit);
        if (heads.isEmpty()) return List.of();

        Map<String, List<Line>> lines = new HashMap<>();
        String marks = heads.stream().map((h) -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(shopId);
        heads.forEach((h) -> args.add(h.id()));
        jdbc.query("""
                SELECT i.order_id, i.product_id, p.product_name, i.unit, i.quantity
                FROM order_items i
                JOIN products p ON p.shop_id = i.shop_id AND p.product_id = i.product_id
                WHERE i.shop_id = ? AND i.status = 'ACTIVE' AND i.order_id IN (""" + marks + ")\nORDER BY i.order_item_id",
                (rs) -> {
                    lines.computeIfAbsent(rs.getString(1), (k) -> new ArrayList<>())
                            .add(new Line(rs.getString(2), rs.getString(3), rs.getString(4), rs.getDouble(5)));
                },
                args.toArray());
        return heads.stream().map((h) -> new OrderRow(h.id(), h.customerId(), h.customerName(), h.at(), h.status(), h.source(),
                lines.getOrDefault(h.id(), List.of()))).toList();
    }

    /** Saves a confirmed order with its lines and returns its id. */
    public String create(String shopId, String customerId, String userId, String source, List<NewLine> lines, LocalDateTime when) {
        String id = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO orders (order_id, shop_id, customer_id, created_at, status, source, current_version, created_by)
                VALUES (?, ?, ?, ?, 'CONFIRMED', ?, 1, ?)
                """, id, shopId, customerId, when, source, userId);
        for (NewLine line : lines) {
            jdbc.update("""
                    INSERT INTO order_items (order_item_id, order_id, shop_id, product_id, quantity, unit, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                    """, "ORI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), id, shopId, line.productId(), line.quantity(), line.unit());
        }
        return id;
    }
}
