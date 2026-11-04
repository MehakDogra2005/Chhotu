package com.dukaansaathi.voice;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.dukaansaathi.customers.CustomerRepository;
import com.dukaansaathi.inventory.InventoryRepository;

/**
 * The words the speech service should listen for: what Saathi understands (kholo, udhaar, bika...) and the names in
 * this shop (customers and products). Boosting them makes the service far less likely to write "caller" for "kholo".
 */
@Component
public class VoiceVocabulary {
    /** The service takes up to 100 single words made of letters only. */
    private static final int MAX_WORDS = 100;
    private static final Pattern LETTERS = Pattern.compile("\\p{L}{3,}");
    private static final List<String> COMMANDS = List.of(
            "kholo", "khol", "chalo", "jao", "udhaar", "udhar", "kitna", "kitni", "batao", "dikhao", "diye", "diya", "baki", "rupaye", "paisa",
            "stock", "samaan", "bikri", "munafa", "margin", "profit", "inventory", "advisor", "customer", "customers", "settings", "reminder",
            "bhejna", "mangwana", "chahiye", "khatam", "purana", "mahine", "hafte", "register", "restock", "insights", "karo", "bika", "sabse",
            "zyada", "product", "payment", "home", "help");

    private final CustomerRepository customers;
    private final InventoryRepository inventory;

    public VoiceVocabulary(CustomerRepository customers, InventoryRepository inventory) {
        this.customers = customers;
        this.inventory = inventory;
    }

    public List<String> forShop(String shopId) {
        Set<String> words = new LinkedHashSet<>(COMMANDS);
        customers.people(shopId).forEach((p) -> addWords(words, p.name()));
        inventory.products(shopId).forEach((p) -> addWords(words, p.name()));
        return words.stream().limit(MAX_WORDS).toList();
    }

    private static void addWords(Set<String> words, String name) {
        var m = LETTERS.matcher(name);
        while (m.find()) words.add(m.group().toLowerCase(Locale.ROOT));
    }
}
