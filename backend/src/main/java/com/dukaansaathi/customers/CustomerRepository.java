package com.dukaansaathi.customers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Customers and their udhaar. Every query filters on shop_id, so a shop can only see its own customers. */
@Repository
public class CustomerRepository {
    /** balancePaise is what the customer owes now; totalCreditPaise is all the credit ever given. */
    public record Balance(String customerId, String name, String phone, long balancePaise, long totalCreditPaise) {}

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

    private static Balance toBalance(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Balance(rs.getString("customer_id"), rs.getString("customer_name"), rs.getString("phone_masked"),
                rs.getLong("balance_paise"), rs.getLong("total_credit_paise"));
    }
}
