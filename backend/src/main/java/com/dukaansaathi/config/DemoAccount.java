package com.dukaansaathi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * If dukaan.demo-email is set, that email address opens the demo shop (Sharma General Store, which comes with
 * products, sales and udhaar). Nothing is created: the address is simply attached to the existing demo owner.
 */
@Component
@Order(2) // after SeedLoader has loaded the sample data
public class DemoAccount implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoAccount.class);
    private static final String DEMO_OWNER = "USR-001";

    private final JdbcTemplate jdbc;
    private final String email;

    public DemoAccount(JdbcTemplate jdbc, @Value("${dukaan.demo-email:}") String email) {
        this.jdbc = jdbc;
        this.email = email.trim().toLowerCase();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty()) return;

        Integer owner = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE user_id = ? AND email = ?", Integer.class, DEMO_OWNER, email);
        if (owner != null && owner > 0) return; // already attached

        Integer taken = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        if (taken != null && taken > 0) {
            log.warn("The demo email {} already belongs to another account, so it cannot open the demo shop. "
                    + "Delete the backend/data folder and start again, or use a different email.", email);
            return;
        }
        jdbc.update("UPDATE users SET email = ? WHERE user_id = ?", email, DEMO_OWNER);
        log.info("Demo account ready: log in with {} to open Sharma General Store.", email);
    }
}
