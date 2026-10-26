package com.dukaansaathi.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsRepository {
    public record Settings(String ownerName, String shopName, String category, String city, String language,
                           String voiceMode, boolean voiceReplies, boolean lowStockAlerts, boolean paymentReminders,
                           String phone, String email) {}

    private final JdbcTemplate jdbc;

    public SettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The owner's profile, their shop's profile and the shop's preferences (defaults when never saved). */
    public Settings find(String userId, String shopId) {
        return jdbc.queryForObject("""
                SELECT u.display_name, u.phone, u.email,
                       s.shop_name, s.business_type, s.city, s.language,
                       COALESCE(ss.voice_mode, 'PUSH_TO_TALK') AS voice_mode,
                       COALESCE(ss.voice_replies, TRUE) AS voice_replies,
                       COALESCE(ss.low_stock_alerts, TRUE) AS low_stock_alerts,
                       COALESCE(ss.payment_reminders, TRUE) AS payment_reminders
                FROM users u
                JOIN shops s ON s.shop_id = ?
                LEFT JOIN shop_settings ss ON ss.shop_id = s.shop_id
                WHERE u.user_id = ?
                """,
                (rs, i) -> new Settings(rs.getString("display_name"), rs.getString("shop_name"), rs.getString("business_type"),
                        rs.getString("city"), rs.getString("language"), rs.getString("voice_mode"),
                        rs.getBoolean("voice_replies"), rs.getBoolean("low_stock_alerts"), rs.getBoolean("payment_reminders"),
                        rs.getString("phone"), rs.getString("email")),
                shopId, userId);
    }

    public void update(String userId, String shopId, Settings s) {
        jdbc.update("UPDATE users SET display_name = ? WHERE user_id = ?", s.ownerName(), userId);
        jdbc.update("UPDATE shops SET shop_name = ?, business_type = ?, city = ?, language = ? WHERE shop_id = ?",
                s.shopName(), s.category(), s.city(), s.language(), shopId);
        int updated = jdbc.update("""
                UPDATE shop_settings SET voice_mode = ?, voice_replies = ?, low_stock_alerts = ?, payment_reminders = ?
                WHERE shop_id = ?
                """, s.voiceMode(), s.voiceReplies(), s.lowStockAlerts(), s.paymentReminders(), shopId);
        if (updated == 0) {  // first time this shop saves its preferences
            jdbc.update("""
                    INSERT INTO shop_settings (shop_id, voice_mode, voice_replies, low_stock_alerts, payment_reminders)
                    VALUES (?, ?, ?, ?, ?)
                    """, shopId, s.voiceMode(), s.voiceReplies(), s.lowStockAlerts(), s.paymentReminders());
        }
    }

    public boolean lowStockAlertsEnabled(String shopId) {
        return jdbc.queryForList("SELECT low_stock_alerts FROM shop_settings WHERE shop_id = ?", Boolean.class, shopId)
                .stream().findFirst().orElse(true);
    }
}
