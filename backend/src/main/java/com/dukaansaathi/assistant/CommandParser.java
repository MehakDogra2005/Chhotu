package com.dukaansaathi.assistant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.dukaansaathi.assistant.AssistantRepository.Alias;
import com.dukaansaathi.customers.CustomerRepository.Balance;
import com.dukaansaathi.inventory.InventoryRepository.ProductStock;

/**
 * Works out what a sentence means, using keywords. It understands English and Hindi written in English letters
 * ("Hinglish"), for example "Chocolate ka stock kitna hai?" or "Ramesh ne 500 de diye".
 * It only decides WHAT is wanted; the business rules are checked later by AssistantService.
 * To understand more sentences, add keywords below, or replace this class with a smarter one: it has one method.
 */
@Component
public class CommandParser {
    public enum Intent { SALES, PROFIT, STOCK_LOOKUP, STOCK_ADD, BALANCE, PAYMENT, CREDIT, LOW_STOCK, RESTOCK_LIST, INSIGHTS,
        TOP_SELLER, BEST_MARGIN, REMINDERS, OLD_UDHAAR, PAYMENT_REGISTER, UNKNOWN }

    /** products and people are the ones the sentence mentions; period is today, week or month. For OLD_UDHAAR quantity is a number of days. */
    public record Parsed(Intent intent, List<ProductStock> products, List<Balance> people, Double quantity, Long amountPaise, String period) {}

    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");

    public Parsed parse(String text, List<ProductStock> products, List<Alias> aliases, List<Balance> customers) {
        String t = normalize(text);
        Double number = firstNumber(t);
        Long paise = number == null ? null : Math.round(number * 100);
        List<ProductStock> foundProducts = findProducts(t, products, aliases);
        List<Balance> foundPeople = findPeople(t, customers);
        String period = has(t, "hafte", "hafta", "week", "saptah") ? "week" : has(t, "mahine", "mahina", "month") ? "month" : "today";

        Intent intent = Intent.UNKNOWN;
        boolean person = !foundPeople.isEmpty();
        boolean udhaarWord = has(t, "udhaar", "udhar", "credit", "samaan", "saman");
        boolean paidWord = has(t, "diye", "diya", "paid", "payment", "chuka", "chukaya", "jama", "received", "mile", "wapas", "bhare");
        boolean most = has(t, "sabse", "most", "best", "top", "highest");

        if (has(t, "insight", "insights", "salah", "suggestion", "suggest") || t.contains("business kaisa")) {
            intent = Intent.INSIGHTS;
        } else if (has(t, "reminder", "reminders", "remind", "tagada", "yaad")) {
            intent = Intent.REMINDERS;      // "Kis-kis ko payment reminder bhejna hai?"
        } else if (!person && has(t, "purana", "purani", "purane", "old", "older")) {
            intent = Intent.OLD_UDHAAR;     // "Kaunse customers ka udhaar 1 mahine se zyada purana hai?"
            number = number == null ? 1.0 : number;
            number = has(t, "din", "day", "days") ? number : has(t, "hafte", "hafta", "week", "weeks") ? number * 7 : number * 30;
        } else if (!person && has(t, "register", "khata", "ledger", "entries", "hisaab", "hisab")) {
            intent = Intent.PAYMENT_REGISTER; // "Aaj ka payment register dikhao"
        } else if (has(t, "restock", "restocking", "reorder") || t.contains("list bana")
                || (number == null && has(t, "mangwana", "mangwau", "mangwaun", "mangwana chahiye"))) {
            intent = Intent.RESTOCK_LIST;  // "Agle hafte ke liye kya stock mangwana chahiye?"
        } else if (most && has(t, "margin", "munafa", "profit", "fayda")) {
            intent = Intent.BEST_MARGIN;   // "Kis product pe sabse zyada margin hai?"
        } else if (most && has(t, "bika", "biki", "bikta", "selling", "seller", "sold", "bikne")) {
            intent = Intent.TOP_SELLER;    // "Kaun sa product sabse zyada bika?"
        } else if (has(t, "khatam", "shortage") || t.contains("low stock") || t.contains("kam stock") || t.contains("out of stock")) {
            intent = Intent.LOW_STOCK;
        } else if (person && number != null && udhaarWord && (padded(t).contains(" ko ") || has(t, "give", "gave", "likh", "likho"))
                && !has(t, "payment", "chuka", "chukaya", "wapas", "jama")) {
            intent = Intent.CREDIT;   // "Ramesh ko 300 ka samaan udhaar diya"
        } else if (person && number != null && paidWord) {
            intent = Intent.PAYMENT;  // "Ramesh ne 500 de diye"
        } else if (person) {
            intent = Intent.BALANCE;  // "Ramesh ka udhaar kitna hai?"
        } else if (has(t, "profit", "munafa", "labh", "margin")) {
            intent = Intent.PROFIT;
        } else if (has(t, "sale", "sales", "bikri", "becha", "revenue", "kamai", "income")) {
            intent = Intent.SALES;
        } else if (!foundProducts.isEmpty() && number != null
                && has(t, "add", "aaye", "aaya", "aa", "jod", "jodo", "badhao", "increase", "received", "mangwa", "kharida", "laya", "mein")) {
            intent = Intent.STOCK_ADD;
        } else if (!foundProducts.isEmpty() && !has(t, "bik", "sold", "bech")) {
            intent = Intent.STOCK_LOOKUP;
        }
        // "Sabse zyada kya bika?" means lately, not only today
        if ((intent == Intent.TOP_SELLER || intent == Intent.BEST_MARGIN) && period.equals("today") && !has(t, "aaj", "today")) period = "month";
        return new Parsed(intent, foundProducts, foundPeople, number, paise, period);
    }

    // ---- who and what the sentence is about

    private List<ProductStock> findProducts(String t, List<ProductStock> products, List<Alias> aliases) {
        Map<String, Integer> score = new HashMap<>();  // product id -> how long the matching words were
        for (ProductStock p : products) {
            String name = normalize(p.name());
            if (hasPhrase(t, name)) score.merge(p.productId(), name.length(), Math::max);
        }
        for (Alias a : aliases) {
            String alias = normalize(a.text());
            if (hasPhrase(t, alias)) score.merge(a.productId(), alias.length(), Math::max);
        }
        if (score.isEmpty()) {  // "bulb" is enough for "LED Bulb"
            for (ProductStock p : products) {
                for (String word : normalize(p.name()).split(" ")) {
                    if (word.length() >= 4 && hasPhrase(t, word)) score.merge(p.productId(), word.length(), Math::max);
                }
            }
        }
        int best = score.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return products.stream().filter((p) -> score.getOrDefault(p.productId(), 0) == best && best > 0).toList();
    }

    private List<Balance> findPeople(String t, List<Balance> customers) {
        List<Balance> exact = new ArrayList<>();
        List<Balance> byFirstName = new ArrayList<>();
        for (Balance c : customers) {
            String name = normalize(c.name());
            if (hasPhrase(t, name)) exact.add(c);
            else if (hasPhrase(t, name.split(" ")[0])) byFirstName.add(c);
        }
        return exact.isEmpty() ? byFirstName : exact;
    }

    // ---- small helpers

    /** Lower case, letters and numbers only: "₹1,200!" becomes "1200". */
    static String normalize(String text) {
        return text.toLowerCase().replaceAll("(?<=\\d),(?=\\d)", "").replaceAll("[^\\p{L}\\p{N}.]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static Double firstNumber(String t) {
        Matcher m = NUMBER.matcher(t);
        return m.find() ? Double.valueOf(m.group()) : null;
    }

    private static String padded(String t) {
        return " " + t + " ";
    }

    private static boolean hasPhrase(String t, String phrase) {
        return !phrase.isBlank() && padded(t).contains(padded(phrase));
    }

    private static boolean has(String t, String... words) {
        for (String word : words) if (hasPhrase(t, word)) return true;
        return false;
    }
}
