package com.dukaansaathi.assistant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Saves what the shopkeeper said (voice_utterances) and the product nicknames used to understand it. */
@Repository
public class AssistantRepository {
    public record Alias(String productId, String text) {}

    public record HistoryItem(String text, LocalDate date, String status) {}

    private final JdbcTemplate jdbc;

    public AssistantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Alias> aliases(String shopId) {
        return jdbc.query("SELECT product_id, alias_text FROM product_aliases WHERE shop_id = ?",
                (rs, i) -> new Alias(rs.getString("product_id"), rs.getString("alias_text")), shopId);
    }

    /** status is NOT_REQUIRED (just an answer) or PENDING (waiting for the shopkeeper to confirm a change). */
    public String save(String shopId, String text, String language, String provider, String structuredJson,
                       String productId, String matchMethod, String status, LocalDateTime when) {
        String id = "VOU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO voice_utterances (utterance_id, shop_id, raw_transcript, language, stt_provider, structured_result_json,
                                              matched_product_id, match_method, confirmation_status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, shopId, text, language, provider, structuredJson, productId, matchMethod, status, when);
        return id;
    }

    /** Moves PENDING to a new status. Returns false when it was not pending, so a change can never be applied twice. */
    public boolean resolve(String shopId, String utteranceId, String newStatus) {
        return jdbc.update("UPDATE voice_utterances SET confirmation_status = ? WHERE utterance_id = ? AND shop_id = ? AND confirmation_status = 'PENDING'",
                newStatus, utteranceId, shopId) == 1;
    }

    public Optional<String> structuredJson(String shopId, String utteranceId) {
        return jdbc.queryForList("SELECT structured_result_json FROM voice_utterances WHERE utterance_id = ? AND shop_id = ?",
                String.class, utteranceId, shopId).stream().findFirst();
    }

    public List<HistoryItem> history(String shopId, int limit) {
        return jdbc.query("""
                SELECT raw_transcript, created_at, confirmation_status
                FROM voice_utterances
                WHERE shop_id = ?
                ORDER BY created_at DESC, utterance_id DESC
                LIMIT ?
                """,
                (rs, i) -> new HistoryItem(rs.getString("raw_transcript"), rs.getObject("created_at", LocalDateTime.class).toLocalDate(),
                        rs.getString("confirmation_status")),
                shopId, limit);
    }
}
