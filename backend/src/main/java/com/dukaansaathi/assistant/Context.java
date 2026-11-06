package com.dukaansaathi.assistant;

import java.util.List;

/**
 * What the conversation is about right now. The server keeps nothing between sentences: the browser sends this back with the
 * next sentence, so "aur kal?" or "10 aur add kar do" can be understood. Everything in it is only a hint: ids are looked up
 * again in the shop's own data before they are used, and it never makes a change on its own.
 *
 * @param intent    what the last answer was about (for example SALES)
 * @param period    the day range of the last answer (today, yesterday, week, month)
 * @param productId the product just talked about
 * @param customerId the customer just talked about
 * @param mode      ORDER while an order is being taken, INVENTORY after "inventory check karni hai", otherwise null
 * @param choices   customer ids Munim Ji just asked the shopkeeper to choose between
 * @param lines     items of the order being taken
 */
public record Context(String intent, String period, String productId, String customerId, String mode, List<String> choices, List<Want> lines) {
    /** One item of an order: a product and how much. */
    public record Want(String productId, double quantity) {}

    public static final String ORDER = "ORDER";
    public static final String INVENTORY = "INVENTORY";

    public static Context empty() {
        return new Context(null, null, null, null, null, List.of(), List.of());
    }

    public List<String> choices() {
        return choices == null ? List.of() : choices;
    }

    public List<Want> lines() {
        return lines == null ? List.of() : lines;
    }

    public boolean ordering() {
        return ORDER.equals(mode);
    }
}
