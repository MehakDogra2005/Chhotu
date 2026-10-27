package com.dukaansaathi.advisor;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the Business Advisor page shows. Money is in paise. Percentage changes compare with the period just before
 * (compareFrom..compareTo) and are null when there is nothing to compare with.
 */
public record AdvisorResponse(
        Period period,
        Totals totals,
        List<Bucket> trend,
        List<Slice> categories,
        List<ProductRow> products,
        Spending spending,
        List<Slice> moneyOut,
        List<Purchase> purchases,
        List<PriceSuggestion> priceSuggestions,
        List<Insight> insights) {

    /** bucket is how the trend is grouped: day, week or month. */
    public record Period(String key, LocalDate from, LocalDate to, LocalDate compareFrom, LocalDate compareTo, String bucket) {}

    /** cost is what the sold goods cost the shop; profit = sales - cost (before shop expenses). */
    public record Totals(long salesPaise, Double salesChangePct, long costPaise, Double costChangePct,
                         long profitPaise, Double profitChangePct, Double marginPct, Double marginChangePoints) {}

    public record Bucket(LocalDate from, LocalDate to, long salesPaise, long costPaise, long profitPaise) {}

    public record Slice(String name, long amountPaise) {}

    /** One product sold in the period. previousSalesPaise and salesChangePct are for the period before. */
    public record ProductRow(String productId, String name, String category, String unit, double quantity,
                             long salesPaise, long profitPaise, Double marginPct, long previousSalesPaise, Double salesChangePct) {}

    /** stockBought is what was spent on deliveries received; netProfit = gross profit - expenses. */
    public record Spending(long stockBoughtPaise, Double stockBoughtChangePct, long expensesPaise, Double expensesChangePct,
                           long netProfitPaise, Long previousNetProfitPaise) {}

    public record Purchase(String purchaseId, LocalDate date, String supplier, String status,
                           long totalPaise, long paidPaise, long duePaise) {}

    /** A product whose price leaves less than the target margin, and the price that would reach it. */
    public record PriceSuggestion(String productId, String name, long purchasePricePaise, long sellingPricePaise,
                                  Double marginPct, long suggestedPricePaise, Double suggestedMarginPct) {}

    /** kind picks the icon: up, down, warn, good or star. */
    public record Insight(String kind, String text) {}
}
