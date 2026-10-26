package com.dukaansaathi.notifications;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.customers.CustomerService;
import com.dukaansaathi.inventory.InventoryRepository;
import com.dukaansaathi.inventory.StockItem;
import com.dukaansaathi.settings.SettingsRepository;
import com.dukaansaathi.settings.SettingsRepository.Switches;

/** The things that need the shopkeeper's attention: products to restock and customers who have not paid on time. */
@Service
public class NotificationService {
    /** type is LOW_STOCK or OVERDUE; link is the page that shows more. */
    public record Notification(String type, String title, String detail, String link) {}

    public record Notifications(int total, List<Notification> items) {}

    private static final int SHOWN_PER_KIND = 5;

    private final InventoryRepository inventory;
    private final SettingsRepository settings;
    private final JdbcTemplate jdbc;
    private final AppClock clock;

    public NotificationService(InventoryRepository inventory, SettingsRepository settings, JdbcTemplate jdbc, AppClock clock) {
        this.inventory = inventory;
        this.settings = settings;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Respects the two switches on the Settings page: a switched-off kind is left out and not counted. */
    public Notifications list(ShopContext who) {
        Switches on = settings.switches(who.shopId());
        List<Notification> items = new ArrayList<>();
        int total = 0;

        if (on.lowStockAlerts()) {
            total += inventory.lowStockCount(who.shopId());
            for (StockItem item : inventory.lowestStock(who.shopId(), true, SHOWN_PER_KIND)) {
                String detail = item.status().equals("OUT") ? "Out of stock" : (long) item.stock() + " " + item.unit() + " left";
                items.add(new Notification("LOW_STOCK", item.name(), detail, "/inventory?search=" + item.name()));
            }
        }
        if (on.paymentReminders()) {
            List<Notification> overdue = overdue(who.shopId());
            total += overdue.size();
            items.addAll(overdue.stream().limit(SHOWN_PER_KIND).toList());
        }
        return new Notifications(total, items);
    }

    /**
     * Customers who have not paid on time. Payments are counted against the oldest credit first, so a customer is
     * overdue when the credit that was due before today is more than everything they have paid so far.
     */
    private List<Notification> overdue(String shopId) {
        LocalDate today = clock.today();
        return jdbc.query("""
                SELECT c.customer_name,
                       SUM(CASE WHEN t.transaction_type = 'CREDIT_ISSUED' AND t.due_date < ? THEN t.amount_paise ELSE 0 END)
                       - SUM(CASE WHEN t.transaction_type = 'REPAYMENT' THEN t.amount_paise
                                  WHEN t.transaction_type = 'ADJUSTMENT' AND t.adjustment_direction = 'DECREASE' THEN t.amount_paise
                                  WHEN t.transaction_type = 'ADJUSTMENT' THEN -t.amount_paise
                                  ELSE 0 END) AS overdue_paise
                FROM customers c
                JOIN credit_transactions t ON t.customer_id = c.customer_id AND t.status = 'RECORDED'
                WHERE c.shop_id = ?
                GROUP BY c.customer_id, c.customer_name
                HAVING SUM(CASE WHEN t.transaction_type = 'CREDIT_ISSUED' AND t.due_date < ? THEN t.amount_paise ELSE 0 END)
                       - SUM(CASE WHEN t.transaction_type = 'REPAYMENT' THEN t.amount_paise
                                  WHEN t.transaction_type = 'ADJUSTMENT' AND t.adjustment_direction = 'DECREASE' THEN t.amount_paise
                                  WHEN t.transaction_type = 'ADJUSTMENT' THEN -t.amount_paise
                                  ELSE 0 END) > 0
                ORDER BY overdue_paise DESC
                """,
                (rs, i) -> new Notification("OVERDUE", rs.getString("customer_name"),
                        CustomerService.rupees(rs.getLong("overdue_paise")) + " overdue", "/customers"),
                today, shopId, today);
    }
}
