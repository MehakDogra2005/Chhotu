package com.dukaansaathi.dashboard;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dukaansaathi.dashboard.DashboardResponse.Debtor;
import com.dukaansaathi.dashboard.DashboardResponse.DayTotal;
import com.dukaansaathi.dashboard.DashboardResponse.Message;
import com.dukaansaathi.dashboard.DashboardResponse.Recording;

/** All the dashboard SQL. Every query filters on shop_id, so a shop can only ever see its own data. */
@Repository
public class DashboardRepository {
    public record SalesTotals(long salesPaise, long profitPaise) {}

    public record UdhaarSummary(int customers, long totalPaise) {}

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SalesTotals salesTotals(String shopId, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(net_amount_paise), 0) AS sales, COALESCE(SUM(gross_profit_paise), 0) AS profit
                FROM sales
                WHERE shop_id = ? AND status = 'COMPLETED' AND sale_date BETWEEN ? AND ?
                """,
                (rs, i) -> new SalesTotals(rs.getLong("sales"), rs.getLong("profit")), shopId, from, to);
    }

    public long itemsSold(String shopId, LocalDate from, LocalDate to) {
        Double units = jdbc.queryForObject("""
                SELECT COALESCE(SUM(si.quantity), 0)
                FROM sale_items si
                JOIN sales s ON s.sale_id = si.sale_id
                WHERE s.shop_id = ? AND s.status = 'COMPLETED' AND s.sale_date BETWEEN ? AND ?
                """, Double.class, shopId, from, to);
        return Math.round(units == null ? 0 : units);
    }

    public List<DayTotal> dailySales(String shopId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT sale_date, SUM(net_amount_paise) AS sales
                FROM sales
                WHERE shop_id = ? AND status = 'COMPLETED' AND sale_date BETWEEN ? AND ?
                GROUP BY sale_date
                ORDER BY sale_date
                """,
                (rs, i) -> new DayTotal(rs.getObject("sale_date", LocalDate.class), rs.getLong("sales")), shopId, from, to);
    }

    public UdhaarSummary udhaarSummary(String shopId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS customers, COALESCE(SUM(balance_paise), 0) AS total
                FROM customer_balance
                WHERE shop_id = ? AND balance_paise > 0
                """,
                (rs, i) -> new UdhaarSummary(rs.getInt("customers"), rs.getLong("total")), shopId);
    }

    public List<Debtor> topDebtors(String shopId, int limit) {
        return jdbc.query("""
                SELECT customer_name, total_credit_paise, balance_paise
                FROM customer_balance
                WHERE shop_id = ? AND balance_paise > 0
                ORDER BY balance_paise DESC
                LIMIT ?
                """,
                (rs, i) -> new Debtor(rs.getString("customer_name"), rs.getLong("total_credit_paise"), rs.getLong("balance_paise")),
                shopId, limit);
    }

    public List<Message> recentUtterances(String shopId, int limit) {
        return jdbc.query("""
                SELECT raw_transcript, created_at
                FROM voice_utterances
                WHERE shop_id = ?
                ORDER BY created_at DESC, utterance_id DESC
                LIMIT ?
                """,
                (rs, i) -> new Message(rs.getString("raw_transcript"), rs.getObject("created_at", LocalDate.class)), shopId, limit);
    }

    public List<Recording> recordings(String shopId, String type, int limit) {
        return jdbc.query("""
                SELECT file_name, status, uploaded_at
                FROM audio_recordings
                WHERE shop_id = ? AND recording_type = ?
                ORDER BY uploaded_at DESC
                LIMIT ?
                """,
                (rs, i) -> new Recording(rs.getString("file_name"), rs.getString("status"),
                        rs.getTimestamp("uploaded_at").toLocalDateTime().toLocalDate()),
                shopId, type, limit);
    }
}
