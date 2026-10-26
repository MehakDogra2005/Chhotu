package com.dukaansaathi.dashboard;

import java.time.LocalDate;
import java.util.List;

import com.dukaansaathi.inventory.StockItem;

/** Everything the Home page shows. Money is in paise; percentage changes are null when there is nothing to compare with. */
public record DashboardResponse(
        Period period,
        Summary summary,
        List<Message> conversations,
        List<StockItem> actionNeeded,
        List<StockItem> inventory,
        Advisor advisor,
        List<Debtor> customers,
        List<Recording> voiceNotes,
        List<Recording> callRecordings) {

    public record Period(String key, LocalDate from, LocalDate to) {}

    public record Summary(long salesPaise, Double salesChangePct, long itemsSold, Double itemsSoldChangePct,
                          long pendingUdhaarPaise, int pendingCustomers, int lowStockItems) {}

    public record Message(String text, LocalDate date) {}

    public record Advisor(long salesPaise, Double salesChangePct, long profitPaise, Double profitChangePct,
                          Double marginPct, Double marginChangePoints, List<DayTotal> trend) {}

    public record DayTotal(LocalDate date, long salesPaise) {}

    public record Debtor(String name, long totalCreditPaise, long pendingPaise) {}

    public record Recording(String fileName, String status, LocalDate uploadedOn) {}
}
