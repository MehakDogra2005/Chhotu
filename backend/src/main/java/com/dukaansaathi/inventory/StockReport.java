package com.dukaansaathi.inventory;

import java.util.List;

import com.dukaansaathi.inventory.InventoryRepository.ReportRow;

/** The "View Stock Report" numbers: what the stock is worth, per category and per product. Money is in paise. */
public record StockReport(long totalValuePaise, List<Category> categories, List<ReportRow> products) {

    /** needRestock is how many products in the category are low or out of stock. */
    public record Category(String name, int items, int needRestock, long valuePaise) {}
}
