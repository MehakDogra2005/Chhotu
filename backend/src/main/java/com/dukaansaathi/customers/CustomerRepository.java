package com.dukaansaathi.customers;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Customers and their udhaar. Every query filters on shop_id, so a shop can only see its own customers. */
@Repository
public class CustomerRepository {
    /** balancePaise is what the customer owes now; totalCreditPaise is all the credit ever given. */
    public record Balance(String customerId, String name, String phone, long balancePaise, long totalCreditPaise) {}

    public record Person(String customerId, String name, String phone, String type) {}

    /** One RECORDED row of the credit ledger. direction and reverses are only set for adjustments. */
    public record Entry(String id, String customerId, String type, long amountPaise, LocalDate date, LocalDate dueDate,
                        String direction, String reverses, String saleId, String note) {}

    private static final String SELECT = """
            SELECT b.customer_id, b.customer_name, c.phone_masked, b.balance_paise, b.total_credit_paise
            FROM customer_balance b
            JOIN customers c ON c.customer_id = b.customer_id
            WHERE b.shop_id = ?
            """;

    private final JdbcTemplate jdbc;

    public CustomerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Balance> find(String shopId, String customerId) {
        return jdbc.query(SELECT + " AND b.customer_id = ?", (rs, i) -> toBalance(rs), shopId, customerId).stream().findFirst();
    }

    public List<Balance> all(String shopId) {
        return jdbc.query(SELECT + " ORDER BY b.customer_name", (rs, i) -> toBalance(rs), shopId);
    }

    public List<Person> people(String shopId) {
        return jdbc.query("SELECT customer_id, customer_name, phone_masked, customer_type FROM customers WHERE shop_id = ? ORDER BY customer_name",
                (rs, i) -> new Person(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), shopId);
    }

    /** The whole RECORDED ledger of the shop, oldest first. customerId narrows it to one customer (or null for all). */
    public List<Entry> ledger(String shopId, String customerId) {
        return jdbc.query("""
                SELECT credit_txn_id, customer_id, transaction_type, amount_paise, transaction_date, due_date,
                       adjustment_direction, reverses_credit_txn_id, sale_id, note
                FROM credit_transactions
                WHERE shop_id = ? AND status = 'RECORDED' AND (CAST(? AS VARCHAR) IS NULL OR customer_id = ?)
                ORDER BY transaction_date, credit_txn_id
                """,
                (rs, i) -> new Entry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10)),
                shopId, customerId, customerId);
    }

    /** sale id -> "Biscuits, Tea": what was bought on each bill that went on udhaar. */
    public Map<String, String> creditSaleItems(String shopId) {
        Map<String, String> items = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT si.sale_id, p.product_name
                FROM sale_items si
                JOIN sales s ON s.sale_id = si.sale_id
                JOIN products p ON p.product_id = si.product_id
                WHERE s.shop_id = ? AND si.sale_id IN (SELECT sale_id FROM credit_transactions WHERE shop_id = ? AND sale_id IS NOT NULL)
                ORDER BY si.sale_id, p.product_name
                """,
                (rs) -> { items.merge(rs.getString(1), rs.getString(2), (a, b) -> a + ", " + b); }, shopId, shopId);
        return items;
    }

    public String create(String shopId, String name, String phone, String type) {
        String id = "CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("INSERT INTO customers (customer_id, shop_id, customer_name, phone_masked, customer_type) VALUES (?, ?, ?, ?, ?)",
                id, shopId, name, phone, type);
        return id;
    }

    public boolean phoneTaken(String shopId, String phone) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM customers WHERE shop_id = ? AND phone_masked = ?", Integer.class, shopId, phone);
        return n != null && n > 0;
    }

    /** Adds one row to the credit ledger. Rows are never changed or deleted; mistakes are fixed by a new row. */
    public String addEntry(String shopId, String customerId, String type, long amountPaise, LocalDate date, LocalDate dueDate, String note) {
        String id = "CRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO credit_transactions (credit_txn_id, shop_id, customer_id, transaction_type, amount_paise,
                                                 transaction_date, status, due_date, note)
                VALUES (?, ?, ?, ?, ?, ?, 'RECORDED', ?, ?)
                """, id, shopId, customerId, type, amountPaise, date, dueDate, note);
        return id;
    }

    // ---- reminders

    public String addReminder(String shopId, String customerId, long amountDuePaise, String channel, String text) {
        String id = "RMD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO payment_reminders (reminder_id, shop_id, customer_id, amount_due_paise, channel, message_text, status, created_at, sent_at)
                VALUES (?, ?, ?, ?, ?, ?, 'SENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, shopId, customerId, amountDuePaise, channel, text);
        return id;
    }

    /** customer id -> the day the last reminder was sent (drafts that were never sent do not count). */
    public Map<String, LocalDate> lastReminders(String shopId) {
        Map<String, LocalDate> last = new HashMap<>();
        jdbc.query("SELECT customer_id, MAX(sent_at) FROM payment_reminders WHERE shop_id = ? AND status = 'SENT' GROUP BY customer_id",
                (rs) -> { last.put(rs.getString(1), rs.getTimestamp(2).toLocalDateTime().toLocalDate()); }, shopId);
        return last;
    }

    public String shopName(String shopId) {
        return jdbc.queryForObject("SELECT shop_name FROM shops WHERE shop_id = ?", String.class, shopId);
    }

    private static Balance toBalance(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Balance(rs.getString("customer_id"), rs.getString("customer_name"), rs.getString("phone_masked"),
                rs.getLong("balance_paise"), rs.getLong("total_credit_paise"));
    }
}
