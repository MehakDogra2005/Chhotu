package com.dukaansaathi.inventory;

/** A product and how much of it is left. status is OUT, LOW or OK. */
public record StockItem(String name, double stock, String unit, double reorderLevel, String status) {

    public static StockItem of(String name, double stock, String unit, double reorderLevel) {
        return new StockItem(name, stock, unit, reorderLevel, statusOf(stock, reorderLevel));
    }

    public static String statusOf(double stock, double reorderLevel) {
        return stock <= 0 ? "OUT" : stock <= reorderLevel ? "LOW" : "OK";
    }
}
