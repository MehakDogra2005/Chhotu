package com.dukaansaathi.translations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.dukaansaathi.assistant.GeminiClient;

/**
 * Product and customer names in every page language. When a name is added, Gemini writes it in all the languages once and the result is
 * kept in name_translations, so showing it later costs nothing. Names that were added before (or when Gemini was down) are done the next
 * time the page asks. The page swaps the names it shows with these, the same way it swaps the fixed texts.
 */
@Service
public class NameTranslations {
    private static final Logger log = LoggerFactory.getLogger(NameTranslations.class);
    private static final int BATCH = 12;
    private static final long RETRY_AFTER_FAILURE_MS = 60_000;

    private static final String NAMES_OF_SHOP = "SELECT product_name AS n FROM products WHERE shop_id = ? UNION SELECT customer_name FROM customers WHERE shop_id = ?";

    private final JdbcTemplate jdbc;
    private final GeminiClient gemini;
    private final ExecutorService worker = Executors.newSingleThreadExecutor((r) -> {
        Thread t = new Thread(r, "name-translations");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean busy;
    private volatile long retryAt;

    public NameTranslations(JdbcTemplate jdbc, GeminiClient gemini) {
        this.jdbc = jdbc;
        this.gemini = gemini;
    }

    /** The shop's names written in the language ("ta"), as typed name to translation. Fast: it only reads what is stored. */
    public Map<String, String> forShop(String shopId, String language) {
        Map<String, String> out = new LinkedHashMap<>();
        jdbc.query("SELECT t.text, t.translated FROM name_translations t WHERE t.language = ? AND t.text IN (SELECT n FROM (" + NAMES_OF_SHOP + ") x)",
                (rs) -> { out.put(rs.getString(1), rs.getString(2)); }, language, shopId, shopId);
        if (!busy && gemini.configured() && System.currentTimeMillis() >= retryAt && !missing(shopId, 1).isEmpty()) fillLater(shopId);
        return out;
    }

    /** A name was just added: translate it in the background. */
    public void warm(String name) {
        if (name == null || name.isBlank() || !gemini.configured()) return;
        worker.execute(() -> {
            try {
                translate(List.of(name.trim()));
            } catch (Exception e) {
                log.warn("Could not translate a name: {}", e.getMessage());
            }
        });
    }

    private void fillLater(String shopId) {
        busy = true;
        worker.execute(() -> {
            try {
                for (int round = 0; round < 10; round++) {
                    List<String> todo = missing(shopId, BATCH);
                    if (todo.isEmpty() || !translate(todo)) break;
                }
            } catch (Exception e) {
                log.warn("Could not translate names: {}", e.getMessage());
            } finally {
                busy = false;
            }
        });
    }

    private List<String> missing(String shopId, int limit) {
        return jdbc.queryForList("SELECT n FROM (" + NAMES_OF_SHOP + ") x WHERE n NOT IN (SELECT text FROM name_translations) ORDER BY n LIMIT " + limit,
                String.class, shopId, shopId);
    }

    /** One Gemini call for a few names. False when it did not work (and then nothing is tried again for a minute). */
    private boolean translate(List<String> names) {
        Map<String, List<String>> result = gemini.names(new ArrayList<>(names));
        if (result == null || result.isEmpty()) {
            retryAt = System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS;
            return false;
        }
        for (Map.Entry<String, List<String>> language : result.entrySet()) {
            for (int i = 0; i < names.size(); i++) {
                jdbc.update("DELETE FROM name_translations WHERE text = ? AND language = ?", names.get(i), language.getKey());
                jdbc.update("INSERT INTO name_translations (text, language, translated) VALUES (?, ?, ?)", names.get(i), language.getKey(), language.getValue().get(i));
            }
        }
        return true;
    }
}
