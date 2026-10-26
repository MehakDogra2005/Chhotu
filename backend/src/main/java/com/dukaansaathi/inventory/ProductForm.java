package com.dukaansaathi.inventory;

/**
 * What the Add Item and Edit Item forms send. Prices are in paise. openingStock is only used when adding:
 * after that, stock changes are recorded as movements (so it cannot be edited here).
 */
public record ProductForm(String name, String category, String unit, long purchasePricePaise, long sellingPricePaise,
                          double reorderLevel, double openingStock) {}
