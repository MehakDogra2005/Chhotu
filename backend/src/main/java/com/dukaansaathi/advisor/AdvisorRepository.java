package com.dukaansaathi.advisor;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dukaansaathi.advisor.AdvisorResponse.Purchase;
import com.dukaansaathi.advisor.AdvisorResponse.Slice;

/** All the Business Advisor SQL. Every query filters on shop_id, so a shop can only ever see its own data. Completed sales only. */
@Repository
public class AdvisorRepository {
    public record Money(long salesPaise, long costPaise, long profitPaise) {}

    public record Day(LocalDate date, Money money) {}

    /** What one product sold in a period. */
    public record ProductSales(String productId, String name, String category, String unit, double quantity,
                               long salesPaise, long costPaise) {}

    /** A product's current list prices. */
    public record Price(String productId, String name, long purchasePricePaise, long sellingPricePaise) {}

    private final JdbcTemplate jdbc;

    public AdvisorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Money totals(String shopId, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(net_amount_paise), 0) AS sales, COALESCE(SUM(cost_amount_paise), 0) AS cost,
                       COALESCE(SUM(gross_profit_paise), 0) AS profit
                FROM sales
                WHERE shop_id = ? AND status = 'COMPLETED' AND sale_date BETWEEN ? AND ?
                """,
                (rs, i) -> new Money(rs.getLong("sales"), rs.getLong("cost"), rs.getLong("profit")), shopId, from, to);
    }

    /** Only the days that had sales. */
    public List<Day> daily(String shopId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT sale_date, SUM(net_amount_paise) AS sales, SUM(cost_amount_paise) AS cost, SUM(gross_profit_paise) AS profit
                FROM sales
                WHERE shop_id = ? AND status = 'COMPLETED' AND sale_date BETWEEN ? AND ?
                GROUP BY sale_date
                ORDER BY sale_date
                """,
                (rs, i) -> new Day(rs.getObject("sale_date", LocalDate.class),
                        new Money(rs.getLong("sales"), rs.getLong("cost"), rs.getLong("profit"))), shopId, from, to);
    }

    public List<Slice> salesByCategory(String shopId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT p.category, SUM(si.net_amount_paise) AS sales
                FROM sale_items si
                JOIN sales s ON s.sale_id = si.sale_id
                JOIN products p ON p.product_id = si.product_id
                WHERE s.shop_id = ? AND s.status = 'COMPLETED' AND s.sale_date BETWEEN ? AND ?
                GROUP BY p.category
                ORDER BY sales DESC
                """,
                (rs, i) -> new Slice(rs.getString("category"), rs.getLong("sales")), shopId, from, to);
    }

    /** Every product sold in the period (archived ones too: their sales still happened), most sold first. */
    public List<ProductSales> productSales(String shopId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT p.product_id, p.product_name, p.category, p.unit,
                       SUM(si.quantity) AS quantity, SUM(si.net_amount_paise) AS sales, SUM(si.cost_amount_paise) AS cost
                FROM sale_items si
                JOIN sales s ON s.sale_id = si.sale_id
                JOIN products p ON p.product_id = si.product_id
                WHERE s.shop_id = ? AND s.status = 'COMPLETED' AND s.sale_date BETWEEN ? AND ?
                GROUP BY p.product_id, p.product_name, p.category, p.unit
                ORDER BY quantity DESC, sales DESC
                """,
                (rs, i) -> new ProductSales(rs.getString("product_id"), rs.getString("product_name"), rs.getString("category"),
                        rs.getString("unit"), rs.getDouble("quantity"), rs.getLong("sales"), rs.getLong("cost")),
                shopId, from, to);
    }

    /** List prices of the products still being sold. */
    public List<Price> activePrices(String shopId) {
        return jdbc.query("""
                SELECT product_id, product_name, unit_purchase_price_paise, unit_selling_price_paise
                FROM products
                WHERE shop_id = ? AND is_active = TRUE
                ORDER BY product_name
                """,
                (rs, i) -> new Price(rs.getString("product_id"), rs.getString("product_name"),
                        rs.getLong("unit_purchase_price_paise"), rs.getLong("unit_selling_price_paise")), shopId);
    }

    /** Money spent on deliveries that arrived in the period. */
    public long stockBought(String shopId, LocalDate from, LocalDate to) {
        Long total = jdbc.queryForObject("""
                SELECT COALESCE(SUM(total_amount_paise), 0)
                FROM purchases
                WHERE shop_id = ? AND status = 'RECEIVED' AND purchase_date BETWEEN ? AND ?
                """, Long.class, shopId, from, to);
        return total == null ? 0 : total;
    }

    /** Orders placed in the period, received or not, newest first. */
    public List<Purchase> purchases(String shopId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT pu.purchase_id, pu.purchase_date, su.supplier_name, pu.status,
                       pu.total_amount_paise, pu.amount_paid_paise, pu.amount_due_paise
                FROM purchases pu
                LEFT JOIN suppliers su ON su.supplier_id = pu.supplier_id
                WHERE pu.shop_id = ? AND pu.purchase_date BETWEEN ? AND ?
                ORDER BY pu.purchase_date DESC, pu.purchase_id DESC
                """,
                (rs, i) -> new Purchase(rs.getString("purchase_id"), rs.getObject("purchase_date", LocalDate.class),
                        rs.getString("supplier_name"), rs.getString("status"), rs.getLong("total_amount_paise"),
                        rs.getLong("amount_paid_paise"), rs.getLong("amount_due_paise")),
                shopId, from, to);
    }

    /** Shop expenses by category, biggest first. */
    public List<Slice> expensesByCategory(String shopId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT expense_category, SUM(amount_paise) AS amount
                FROM expenses
                WHERE shop_id = ? AND expense_date BETWEEN ? AND ?
                GROUP BY expense_category
                ORDER BY amount DESC
                """,
                (rs, i) -> new Slice(rs.getString("expense_category"), rs.getLong("amount")), shopId, from, to);
    }
}
