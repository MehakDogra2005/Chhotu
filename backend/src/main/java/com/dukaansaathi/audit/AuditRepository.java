package com.dukaansaathi.audit;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The audit trail: one row for every change made to shop data, so it is always possible to see who did what. */
@Repository
public class AuditRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** source is UI (a button) or VOICE (a spoken or typed command). oldValue and newValue describe the change (a Map is fine, or null). */
    public void record(String shopId, String userId, String entityType, String entityId, String action,
                       Object oldValue, Object newValue, String source) {
        jdbc.update("""
                INSERT INTO audit_log (audit_id, shop_id, actor_user_id, entity_type, entity_id, action,
                                       old_value_json, new_value_json, source, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                "AUD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                shopId, userId, entityType, entityId, action, toJson(oldValue), toJson(newValue), source);
    }

    private String toJson(Object value) {
        try {
            return value == null ? null : json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
