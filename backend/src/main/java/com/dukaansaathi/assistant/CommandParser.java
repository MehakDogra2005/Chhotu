package com.dukaansaathi.assistant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.dukaansaathi.assistant.AssistantRepository.Alias;
import com.dukaansaathi.assistant.Context.Want;
import com.dukaansaathi.customers.CustomerRepository.Balance;
import com.dukaansaathi.inventory.InventoryRepository.ProductStock;

/**
 * Works out what a sentence means, using keywords. It understands English and Hindi written in English letters
 * ("Hinglish"), for example "Chocolate ka stock kitna hai?" or "Ramesh ne 500 de diye".
 * It only decides WHAT is wanted; the business rules are checked later by AssistantService.
 * The conversation so far (Context) lets it understand short follow-ups: "Aur kal?", "10 aur add kar do", "Uska udhaar?".
 * To understand more sentences, add keywords below, or replace this class with a smarter one: the service only needs
 * parse(), items() and pick().
 */
@Component
public class CommandParser {
    public enum Intent { SALES, PROFIT, EXPENSES, STOCK_LOOKUP, STOCK_ADD, INVENTORY_CHECK, BALANCE, PAYMENT, CREDIT, LOW_STOCK,
        RESTOCK_LIST, INSIGHTS, TOP_SELLER, BEST_MARGIN, REMINDERS, OLD_UDHAAR, PAYMENT_REGISTER, ORDER_START, NAVIGATE, UNKNOWN }

    /** A page of the app: path is its address (it may carry a filter), name is what Saathi says. */
    public record Page(String path, String name) {}

    /**
     * products and people are the ones the sentence mentions (or, for a follow-up, the ones talked about before);
     * period is today, yesterday, week or month and periodGiven says whether the sentence named one.
     * For OLD_UDHAAR quantity is a number of days. page is set for NAVIGATE. followUp is true for a short "aur ...?" style sentence.
     */
    public record Parsed(Intent intent, List<ProductStock> products, List<Balance> people, Double quantity, Long amountPaise,
                         String period, boolean periodGiven, Page page, boolean followUp) {}

    /** What an order sentence asked for: the products found, words that matched no product, and products that could be either of several. */
    public record Items(List<Want> found, List<String> missing, List<String> unclear) {}

    public static final Page ORDERS_PAGE = new Page("/customers?view=orders", "Orders");

    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");

    private static final Map<String, Double> NUMBER_WORDS = new HashMap<>();
    static {
        String[][] words = {
            {"ek", "1"}, {"one", "1"}, {"do", "2"}, {"two", "2"}, {"teen", "3"}, {"three", "3"}, {"char", "4"}, {"chaar", "4"}, {"four", "4"},
            {"paanch", "5"}, {"panch", "5"}, {"five", "5"}, {"chhe", "6"}, {"chah", "6"}, {"six", "6"}, {"saat", "7"}, {"seven", "7"},
            {"aath", "8"}, {"eight", "8"}, {"nau", "9"}, {"nine", "9"}, {"das", "10"}, {"ten", "10"}, {"gyarah", "11"}, {"barah", "12"},
            {"pandrah", "15"}, {"bees", "20"}, {"pachees", "25"}, {"pachas", "50"}, {"sau", "100"},
            {"aadha", "0.5"}, {"adha", "0.5"}, {"half", "0.5"}, {"dedh", "1.5"}, {"dhai", "2.5"}};
        for (String[] w : words) NUMBER_WORDS.put(w[0], Double.valueOf(w[1]));
    }

    /** Words in an order sentence that are not part of a product's name. */
    private static final Set<String> FILLER = Set.of("ka", "ki", "ke", "ko", "ek", "do", "packet", "packets", "pkt", "kilo", "kg", "kgs", "litre",
            "liter", "ltr", "piece", "pieces", "pcs", "dozen", "gram", "grams", "gm", "ml", "bottle", "bottles", "box", "chahiye", "chahie",
            "hai", "hain", "lena", "lene", "leni", "order", "orders", "mein", "me", "bhi", "please", "plz", "dena", "dijiye", "de", "diye",
            "karo", "kar", "likho", "likh", "add", "wala", "wali", "wale", "bas", "sirf", "total", "new", "naya", "aur", "and", "or", "the", "of", "i", "want", "need", "ji",
            "pehla", "pehle", "pahla", "dusra", "doosra", "dusre", "teesra", "tisra", "first", "second", "third", "1st", "2nd", "3rd", "wo", "woh", "yeh", "ye", "haan", "han", "nahi");
    private static final Set<String> VERBS_AFTER_DO = Set.of("karo", "kar", "likh", "likho", "de", "dena", "dijiye", "rakh", "add", "hai", "hain", "na", "ji", "please");

    public Parsed parse(String text, List<ProductStock> products, List<Alias> aliases, List<Balance> customers, Context ctx) {
        String t = normalize(text);
        Double number = firstNumber(t);
        Long paise = number == null ? null : Math.round(number * 100);
        List<ProductStock> foundProducts = findProducts(t, products, aliases);
        List<Balance> foundPeople = findPeople(t, customers);

        boolean weekWord = has(t, "hafte", "hafta", "week", "saptah");
        boolean monthWord = has(t, "mahine", "mahina", "month");
        boolean yesterdayWord = has(t, "kal", "yesterday");
        boolean todayWord = has(t, "aaj", "today");
        String period = weekWord ? "week" : monthWord ? "month" : yesterdayWord ? "yesterday" : "today";
        boolean periodGiven = weekWord || monthWord || yesterdayWord || todayWord;

        boolean udhaarWord = has(t, "udhaar", "udhar", "credit", "samaan", "saman");
        boolean paidWord = has(t, "diye", "diya", "paid", "payment", "chuka", "chukaya", "jama", "received", "mile", "wapas", "bhare");
        boolean addWord = has(t, "add", "ad", "aaye", "aaya", "aa", "jod", "jodo", "badhao", "increase", "received", "mangwa", "kharida", "laya", "mein");
        boolean most = has(t, "sabse", "most", "best", "top", "highest");

        // Follow-ups: "uska udhaar?" means the customer just talked about; "10 aur add kar do" the product just talked about
        boolean itWord = has(t, "iska", "isme", "isko", "iski", "iske", "isse", "it", "this", "that");
        boolean theyWord = has(t, "unka", "inka", "unko", "inko", "unhe", "inhe", "unki", "inki", "wo", "woh", "unse", "inse");
        boolean eitherWord = has(t, "uska", "usko", "uski", "uske", "usme", "usse");
        boolean aboutCustomer = ctx.customerId() != null && (ctx.intent() == null || switch (ctx.intent()) {
            case "BALANCE", "PAYMENT", "CREDIT", "ORDER_START", "REMINDERS" -> true;
            default -> false;
        });
        if (foundPeople.isEmpty() && ctx.customerId() != null && (theyWord || (eitherWord && aboutCustomer) || (number != null && paidWord && foundProducts.isEmpty()))) {
            foundPeople = customers.stream().filter((c) -> c.customerId().equals(ctx.customerId())).toList();
        }
        if (foundProducts.isEmpty() && foundPeople.isEmpty() && ctx.productId() != null
                && (itWord || (eitherWord && !aboutCustomer) || (number != null && addWord))) {
            foundProducts = products.stream().filter((p) -> p.productId().equals(ctx.productId())).toList();
        }
        boolean person = !foundPeople.isEmpty();
        boolean followUp = t.startsWith("aur ") || t.startsWith("and ") || t.startsWith("also ") || t.startsWith("what about")
                || t.startsWith("ya ") || t.split(" ").length <= 3;

        Page page = pageFor(t);
        Intent intent = Intent.UNKNOWN;

        // "Clicking" words: "Chalo aaj ka profit batao" is a question, not a request to open a page: a weak word like "chalo"
        // only counts when the sentence asks for no answer
        boolean openStrong = has(t, "kholo", "kholiye", "khol do", "open", "go to", "navigate", "le chalo", "le chal", "par le", "pe le", "page par", "page pe");
        boolean openWeak = has(t, "chalo", "jao", "par chalo", "pe chalo", "par jao", "pe jao");
        boolean asks = has(t, "batao", "bata", "batana", "kitna", "kitni", "kya", "kaun", "kab", "kaise", "hai", "dikhao", "show", "tell");
        boolean checkWord = has(t, "check", "dekh", "dekhni", "dekhna", "dekhte", "dekhenge", "dekhu", "jaanch", "jaach", "review", "status");

        boolean orderWord = has(t, "order", "orders", "ordar");
        boolean takeWord = has(t, "lena", "lene", "lenge", "leni", "lun", "lu", "lo", "lete", "lijiye", "book", "likhna", "likho", "likh",
                "note", "karna", "karne", "banana", "banao", "naya", "new", "take", "place", "start", "shuru", "darj", "save");
        boolean stockOrder = has(t, "restock", "restocking", "reorder", "mangwana", "mangwau", "supplier");

        if (orderWord && takeWord && !stockOrder) {
            intent = Intent.ORDER_START;           // "Humein order lene hain", "Ramesh ka order lena hai"
        } else if (orderWord && !stockOrder && !person && number == null) {
            intent = Intent.NAVIGATE;              // "Orders dikhao"
            page = ORDERS_PAGE;
        } else if (page != null && !person && number == null && (openStrong || (openWeak && !asks))) {
            intent = Intent.NAVIGATE;              // "Inventory kholo", "Udhaar page par le chalo", "Home chalo"
        } else if (has(t, "insight", "insights", "salah", "suggestion", "suggest") || t.contains("business kaisa")) {
            intent = Intent.INSIGHTS;
        } else if (has(t, "reminder", "reminders", "remind", "tagada", "yaad")) {
            intent = Intent.REMINDERS;      // "Kis-kis ko payment reminder bhejna hai?"
        } else if (!person && has(t, "purana", "purani", "purane", "old", "older")) {
            intent = Intent.OLD_UDHAAR;     // "Kaunse customers ka udhaar 1 mahine se zyada purana hai?"
            number = number == null ? 1.0 : number;
            number = has(t, "din", "day", "days") ? number : has(t, "hafte", "hafta", "week", "weeks") ? number * 7 : number * 30;
        } else if (!person && (has(t, "register", "khata", "ledger", "entries") || (has(t, "hisaab", "hisab") && has(t, "payment", "udhaar", "udhar")))) {
            intent = Intent.PAYMENT_REGISTER; // "Aaj ka payment register dikhao"
        } else if (has(t, "restock", "restocking", "reorder") || t.contains("list bana")
                || (number == null && has(t, "mangwana", "mangwau", "mangwaun", "mangwana chahiye"))) {
            intent = Intent.RESTOCK_LIST;  // "Agle hafte ke liye kya stock mangwana chahiye?"
        } else if (most && has(t, "margin", "munafa", "profit", "fayda")) {
            intent = Intent.BEST_MARGIN;   // "Kis product pe sabse zyada margin hai?"
        } else if (most && has(t, "bika", "biki", "bikta", "selling", "seller", "sold", "bikne", "beka", "bikaa", "bikka")) {
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
        } else if (has(t, "profit", "munafa", "labh", "margin", "nafa", "fayda")) {
            intent = Intent.PROFIT;
        } else if (has(t, "kharcha", "kharche", "kharch", "expense", "expenses", "spending", "spent")) {
            intent = Intent.EXPENSES;
        } else if (has(t, "sale", "sales", "bikri", "becha", "revenue", "kamai", "kamaya", "kamaye", "income", "earning", "earnings", "turnover", "hisaab", "hisab", "galla")) {
            intent = Intent.SALES;
        } else if (!foundProducts.isEmpty() && number != null && addWord) {
            intent = Intent.STOCK_ADD;
        } else if (!foundProducts.isEmpty() && !has(t, "bik", "sold", "bech")) {
            intent = Intent.STOCK_LOOKUP;
        } else if (page != null && page.path().equals("/inventory") && number == null && (checkWord || openStrong)) {
            intent = Intent.INVENTORY_CHECK; // "Mujhe aaj ki inventory check karni hai"
        }
        // "Sabse zyada kya bika?" means lately, not only today
        if ((intent == Intent.TOP_SELLER || intent == Intent.BEST_MARGIN) && period.equals("today") && !todayWord) period = "month";
        return new Parsed(intent, foundProducts, foundPeople, number, paise, period, periodGiven, page, followUp);
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

    /** Does the sentence contain any of these words or phrases? */
    public boolean said(String text, String... words) {
        return has(normalize(text), words);
    }

    /** Does the sentence begin with one of these words? ("Aur ek doodh" adds to an order; "Do Maggi" starts it again.) */
    public boolean startsWith(String text, String... words) {
        String t = normalize(text);
        for (String word : words) if (t.equals(word) || t.startsWith(word + " ")) return true;
        return false;
    }

    /** The customers named in a sentence (used while taking an order, where the sentence is not otherwise parsed). */
    public List<Balance> people(String text, List<Balance> customers) {
        return findPeople(normalize(text), customers);
    }

    /**
     * The shopkeeper was asked which of several customers (same name) they meant. Understands the last digits of the phone number,
     * "pehla / dusra / teesra" (first / second / third) and a different word of the name (the surname). Null when it cannot tell.
     */
    public Balance pick(String text, List<Balance> choices) {
        String t = normalize(text);
        List<Balance> hits = new ArrayList<>();
        String digits = t.replaceAll("[^0-9]", "");
        if (digits.length() >= 2) {
            for (Balance c : choices) if (c.phone() != null && c.phone().replaceAll("[^0-9]", "").endsWith(digits)) hits.add(c);
            if (hits.size() == 1) return hits.get(0);
        }
        int index = has(t, "pehla", "pehle", "pahla", "first", "1st") ? 0 : has(t, "dusra", "doosra", "dusre", "second", "2nd") ? 1
                : has(t, "teesra", "tisra", "third", "3rd") ? 2 : -1;
        if (index >= 0 && index < choices.size()) return choices.get(index);
        hits.clear();
        for (Balance c : choices) {
            String[] parts = normalize(c.name()).split(" ");
            for (int i = 1; i < parts.length; i++) if (parts[i].length() >= 3 && hasPhrase(t, parts[i])) { hits.add(c); break; }
        }
        return hits.size() == 1 ? hits.get(0) : null;
    }

    /**
     * Reads an order such as "Do Maggi, ek kilo cheeni aur ek doodh ka packet" into products and quantities. A part with no number
     * means one. ignore holds words to skip (the customer's name).
     */
    public Items items(String text, List<ProductStock> products, List<Alias> aliases, Set<String> ignore) {
        List<Want> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> unclear = new ArrayList<>();
        for (String part : text.toLowerCase().split("\\s*(,|;|\\+|\\baur\\b|\\band\\b|\\btatha\\b|\\bphir\\b)\\s*")) {
            String t = normalize(part);
            if (t.isBlank()) continue;
            String[] tokens = t.split(" ");

            Double quantity = null;
            List<String> rest = new ArrayList<>();
            for (int i = 0; i < tokens.length; i++) {
                String w = tokens[i];
                Matcher m = NUMBER.matcher(w);
                boolean doIsVerb = w.equals("do") && (i + 1 >= tokens.length || VERBS_AFTER_DO.contains(tokens[i + 1]));
                if (quantity == null && m.find()) quantity = Double.valueOf(m.group());
                else if (quantity == null && NUMBER_WORDS.containsKey(w) && !doIsVerb) quantity = NUMBER_WORDS.get(w);
                else if (!FILLER.contains(w) && !ignore.contains(w) && !m.find(0)) rest.add(w);
            }
            if (rest.isEmpty()) continue; // only numbers and filler: nothing to look up

            List<ProductStock> hits = findProducts(t, products, aliases);
            if (hits.size() == 1) {
                found.add(new Want(hits.get(0).productId(), quantity == null ? 1 : quantity));
            } else if (hits.size() > 1) {
                unclear.add(hits.stream().limit(3).map(ProductStock::name).reduce((a, b) -> a + " / " + b).orElse(""));
            } else {
                missing.add(String.join(" ", rest));
            }
        }
        return new Items(found, missing, unclear);
    }

    /** The words of the names in `people`, to skip while reading an order sentence. */
    public Set<String> nameWords(List<Balance> people) {
        Set<String> words = new LinkedHashSet<>();
        for (Balance c : people) words.addAll(List.of(normalize(c.name()).split(" ")));
        return words;
    }

    static Page pageFor(String t) {
        if (has(t, "inventory", "stock", "samaan", "saman", "items", "maal")) return new Page("/inventory", "Inventory");
        if (has(t, "advisor", "profit", "munafa", "business")) return new Page("/advisor", "Business Advisor");
        if (has(t, "customer", "customers", "udhaar", "udhar", "grahak", "khata")) return new Page("/customers", "Customer Handling");
        if (has(t, "settings", "setting")) return new Page("/settings", "Settings");
        if (has(t, "help", "madad")) return new Page("/help", "Help");
        if (has(t, "conversations", "conversation", "baatein")) return new Page("/conversations", "Conversations");
        if (has(t, "home", "ghar", "dashboard")) return new Page("/", "Home");
        return null;
    }

    // ---- small helpers

    /** Lower case, letters and numbers only: "₹1,200!" becomes "1200". */
    static String normalize(String text) {
        // A full stop from the speech service ("diye.") must not stick to the word; dots stay only inside numbers (1.5)
        return text.toLowerCase().replaceAll("(?<=\\d),(?=\\d)", "").replaceAll("[^\\p{L}\\p{N}.]+", " ")
                .replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ").replaceAll("\\s+", " ").trim();
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
