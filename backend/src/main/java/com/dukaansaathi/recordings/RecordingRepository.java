package com.dukaansaathi.recordings;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Voice notes and call recordings a shop uploaded. Every query filters on shop_id. */
@Repository
public class RecordingRepository {
    /** status: UPLOADED (no text yet), NEEDS_REVIEW (has text to check), PROCESSED, FAILED. */
    public record Recording(String recordingId, String type, String fileName, Integer durationSeconds, String transcript,
                            String status, boolean consent, LocalDateTime uploadedAt) {}

    private static final String SELECT = """
            SELECT recording_id, recording_type, file_name, duration_seconds, transcript, status, consent_confirmed, uploaded_at
            FROM audio_recordings WHERE shop_id = ?
            """;

    private final JdbcTemplate jdbc;

    public RecordingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Recording> latest(String shopId, int limit) {
        return jdbc.query(SELECT + " ORDER BY uploaded_at DESC LIMIT ?", (rs, i) -> toRecording(rs), shopId, limit);
    }

    public Optional<Recording> find(String shopId, String recordingId) {
        return jdbc.query(SELECT + " AND recording_id = ?", (rs, i) -> toRecording(rs), shopId, recordingId).stream().findFirst();
    }

    /** When the same file was uploaded before, the day it was. */
    public Optional<LocalDate> uploadedBefore(String shopId, String contentHash) {
        return jdbc.query("SELECT uploaded_at FROM audio_recordings WHERE shop_id = ? AND content_hash = ?",
                (rs, i) -> rs.getTimestamp(1).toLocalDateTime().toLocalDate(), shopId, contentHash).stream().findFirst();
    }

    public void add(String id, String shopId, String type, String fileName, String contentHash, Integer durationSeconds,
                    boolean consent, String userId, LocalDateTime uploadedAt, LocalDate keepUntil) {
        jdbc.update("""
                INSERT INTO audio_recordings (recording_id, shop_id, recording_type, file_name, content_hash, duration_seconds,
                                              consent_confirmed, status, uploaded_by, uploaded_at, retention_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'UPLOADED', ?, ?, ?)
                """, id, shopId, type, fileName, contentHash, durationSeconds, consent, userId, uploadedAt, keepUntil);
    }

    public void saveTranscript(String shopId, String recordingId, String transcript) {
        jdbc.update("UPDATE audio_recordings SET transcript = ?, transcript_provider = 'Typed by shopkeeper', status = 'NEEDS_REVIEW' WHERE shop_id = ? AND recording_id = ?",
                transcript, shopId, recordingId);
    }

    private static Recording toRecording(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Recording(rs.getString(1), rs.getString(2), rs.getString(3), (Integer) rs.getObject(4), rs.getString(5),
                rs.getString(6), rs.getBoolean(7), rs.getTimestamp(8).toLocalDateTime());
    }
}
