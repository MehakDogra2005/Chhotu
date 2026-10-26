package com.dukaansaathi.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findIdByPhone(String phone) {
        return jdbc.queryForList("SELECT user_id FROM users WHERE phone = ?", String.class, phone).stream().findFirst();
    }

    public Optional<String> findIdByEmail(String email) {
        return jdbc.queryForList("SELECT user_id FROM users WHERE email = ?", String.class, email).stream().findFirst();
    }

    /** A new user. People sign up with a phone number or an email, so one of the two is null. */
    public String create(String phone, String email, String name) {
        String userId = "USR-" + shortId();
        jdbc.update("INSERT INTO users (user_id, phone, email, display_name) VALUES (?, ?, ?, ?)", userId, phone, email, name);
        return userId;
    }

    public boolean hasShop(String userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user_shops WHERE user_id = ?", Integer.class, userId);
        return count != null && count > 0;
    }

    /** Saves the owner's name, creates the shop and makes the user its owner. */
    public void createShop(String userId, String ownerName, String shopName, String category, String language) {
        String shopId = "SHOP-" + shortId();
        jdbc.update("UPDATE users SET display_name = ? WHERE user_id = ?", ownerName, userId);
        jdbc.update("INSERT INTO shops (shop_id, shop_name, business_type, language) VALUES (?, ?, ?, ?)",
                shopId, shopName, category, language);
        jdbc.update("INSERT INTO user_shops (user_id, shop_id, role) VALUES (?, ?, 'OWNER')", userId, shopId);
    }

    public Optional<ShopContext> findShopContext(String userId) {
        return jdbc.query("""
                SELECT u.user_id, u.display_name, s.shop_id, s.shop_name, s.city
                FROM users u
                JOIN user_shops us ON us.user_id = u.user_id
                JOIN shops s ON s.shop_id = us.shop_id
                WHERE u.user_id = ?
                ORDER BY s.shop_id
                LIMIT 1
                """,
                (rs, i) -> new ShopContext(rs.getString("user_id"), rs.getString("display_name"),
                        rs.getString("shop_id"), rs.getString("shop_name"), rs.getString("city")),
                userId).stream().findFirst();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
