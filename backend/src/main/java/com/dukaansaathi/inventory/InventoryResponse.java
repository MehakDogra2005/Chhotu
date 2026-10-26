package com.dukaansaathi.inventory;

import java.time.LocalDate;
import java.util.List;

/** Everything the Inventory page shows. Money is in paise. */
public record InventoryResponse(LocalDate today, Counts counts, List<Item> items, List<StockItem> alerts) {

    /** Numbers for the cards and the tabs. Low means stock above 0 but at or under the reorder level; Out means none left. */
    public record Counts(int all, int low, int out, int onOrder, long valuePaise) {}

    public record Item(String name, String category, double stock, String unit, double reorderLevel,
                       String status, LocalDate lastUpdated, boolean onOrder) {}
}
