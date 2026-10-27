package com.dukaansaathi.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Loads the sample CSVs from db/ into every table that is still empty (so a new table gets filled on the next start). */
@Component
@Order(1) // before DemoAccount
public class SeedLoader implements ApplicationRunner {
    // Parent tables first, so foreign keys are satisfied.
    private static final List<String> TABLES = List.of(
            "shops", "users", "user_shops", "suppliers", "customers", "products", "purchases", "purchase_items", "sales",
            "sale_items", "credit_transactions", "inventory_movements", "voice_utterances", "audio_recordings", "product_aliases", "audit_log", "expenses",
            "payment_reminders");

    private final JdbcTemplate jdbc;
    private final Path seedDir;

    public SeedLoader(JdbcTemplate jdbc, @Value("${dukaan.seed-dir}") String seedDir) {
        this.jdbc = jdbc;
        this.seedDir = Path.of(seedDir).toAbsolutePath();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        for (String table : TABLES) {
            Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            if (rows != null && rows == 0) load(table);
        }
    }

    private void load(String table) throws IOException {
        Path csv = seedDir.resolve(table + ".csv");
        String[] columns;
        try (BufferedReader in = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            columns = in.readLine().split(",");  // the header row names the columns
        }
        String names = String.join(", ", columns);
        // Empty CSV cells become NULL
        String values = Arrays.stream(columns).map(c -> "NULLIF(" + c + ", '')").collect(Collectors.joining(", "));
        String file = csv.toString().replace('\\', '/');
        jdbc.update("INSERT INTO " + table + " (" + names + ") SELECT " + values
                + " FROM CSVREAD('" + file + "', NULL, 'charset=UTF-8')");
    }
}
