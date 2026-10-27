package com.dukaansaathi.assistant;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.advisor.AdvisorRepository;
import com.dukaansaathi.advisor.AdvisorRepository.ProductSales;
import com.dukaansaathi.assistant.CommandParser.Parsed;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.customers.CustomerRepository;
import com.dukaansaathi.customers.CustomerRepository.Balance;
import com.dukaansaathi.customers.CustomerRepository.Entry;
import com.dukaansaathi.customers.CustomerRepository.Person;
import com.dukaansaathi.customers.CustomerService;
import com.dukaansaathi.customers.UdhaarBook;
import com.dukaansaathi.customers.UdhaarBook.Account;
import com.dukaansaathi.dashboard.DashboardRepository;
import com.dukaansaathi.dashboard.DashboardRepository.SalesTotals;
import com.dukaansaathi.inventory.InventoryRepository;
import com.dukaansaathi.inventory.InventoryRepository.ProductStock;
import com.dukaansaathi.inventory.InventoryService;
import com.dukaansaathi.inventory.StockItem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The voice assistant's brain. interpret() understands a sentence and either answers it, or proposes a change
 * (add stock, record a payment, give credit). A proposed change is saved on the server and only happens after
 * the shopkeeper confirms it, through execute(). The numbers always come from the database, never from guesses.
 */
@Service
public class AssistantService {
    /** One thing that can be done once confirmed. label is the text on the confirm button. */
    public record Action(String type, String productId, String customerId, Double quantity, Long amountPaise, String label) {}

    /** What the browser receives. options is empty for a plain answer; otherwise each option is one action to confirm. */
    public record Reply(String utteranceId, String message, List<String> options) {}

    public record Done(String message) {}

    private record Stored(String intent, List<Action> actions) {}

    private record Outcome(String message, List<Action> actions) {
        static Outcome say(String message) {
            return new Outcome(message, List.of());
        }
    }

    private static final int MAX_TEXT = 300;
    private static final int MAX_OPTIONS = 6;
    private static final int LIST_SIZE = 8;
    private static final String NL = "\n";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM");
    private static final Pattern HINGLISH = Pattern.compile("\\b(ka|ki|ke|hai|karo|batao|ne|ko|diye|diya|kitna|kitni|bacha|aaj|hafte|mahine)\\b");
    private static final String HELP = "I did not understand that. You can ask, for example: \"Chocolate ka stock kitna hai?\", "
            + "\"Aaj ki sale batao\", \"Ramesh ka udhaar kitna hai?\", or tell me: \"10 Chocolate add karo\", \"Ramesh ne 500 de diye\".";

    private final AssistantRepository repo;
    private final CommandParser parser;
    private final InventoryRepository inventory;
    private final InventoryService inventoryService;
    private final CustomerRepository customers;
    private final CustomerService customerService;
    private final DashboardRepository dashboard;
    private final AdvisorRepository advisor;
    private final AppClock clock;
    private final ObjectMapper json;

    public AssistantService(AssistantRepository repo, CommandParser parser, InventoryRepository inventory,
                            InventoryService inventoryService, CustomerRepository customers, CustomerService customerService,
                            DashboardRepository dashboard, AdvisorRepository advisor, AppClock clock, ObjectMapper json) {
        this.repo = repo;
        this.parser = parser;
        this.inventory = inventory;
        this.inventoryService = inventoryService;
        this.customers = customers;
        this.customerService = customerService;
        this.dashboard = dashboard;
        this.advisor = advisor;
        this.clock = clock;
        this.json = json;
    }

    // ------------------------------------------------------------------ understanding

    /** via is "voice" (spoken into the microphone) or "typed". */
    @Transactional
    public Reply interpret(ShopContext who, String text, String via) {
        if (text == null || text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say or type something first");
        if (text.length() > MAX_TEXT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That is too long. Please keep it short");

        String shop = who.shopId();
        Parsed p = parser.parse(text, inventory.products(shop), repo.aliases(shop), customers.all(shop));

        Outcome outcome = switch (p.intent()) {
            case SALES -> sales(shop, p);
            case PROFIT -> profit(shop, p);
            case STOCK_LOOKUP -> stockLookup(p);
            case STOCK_ADD -> stockAdd(p);
            case BALANCE -> balance(p);
            case PAYMENT -> payment(p);
            case CREDIT -> credit(p);
            case LOW_STOCK -> lowStock(shop);
            case RESTOCK_LIST -> restockList(shop);
            case INSIGHTS -> insights(shop);
            case TOP_SELLER -> topSeller(shop, p);
            case BEST_MARGIN -> bestMargin(shop, p);
            case REMINDERS -> reminders(shop);
            case OLD_UDHAAR -> oldUdhaar(shop, p);
            case PAYMENT_REGISTER -> register(shop, p);
            case UNKNOWN -> Outcome.say(HELP);
        };

        boolean needsConfirm = !outcome.actions().isEmpty();
        String id = repo.save(shop, text.trim(), HINGLISH.matcher(text.toLowerCase()).find() ? "hinglish" : "en",
                "voice".equals(via) ? "Browser" : "Typed", stored(p.intent().name(), outcome.actions()),
                p.products().size() == 1 ? p.products().get(0).productId() : null,
                p.products().isEmpty() ? "INTENT_ONLY" : "CATALOGUE_MATCH", needsConfirm ? "PENDING" : "NOT_REQUIRED", clock.today().atTime(LocalTime.now()));
        return new Reply(id, outcome.message(), outcome.actions().stream().map(Action::label).toList());
    }

    // ------------------------------------------------------------------ doing

    /** Carries out one of the options of an earlier reply. It works once: a second tap finds nothing pending. */
    @Transactional
    public Done execute(ShopContext who, String utteranceId, int choice) {
        String saved = repo.structuredJson(who.shopId(), utteranceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nothing to confirm"));
        List<Action> actions = read(saved).actions();
        if (choice < 0 || choice >= actions.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown option");
        if (!repo.resolve(who.shopId(), utteranceId, "CONFIRMED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That was already done or cancelled");
        }

        Action a = actions.get(choice);
        return switch (a.type()) {
            case "STOCK_ADD" -> {
                ProductStock now = inventoryService.addStock(who, a.productId(), a.quantity(), utteranceId);
                yield new Done("Added " + fmt(a.quantity()) + " " + now.unit() + " to " + now.name() + ". Stock is now " + fmt(now.stock()) + ".");
            }
            case "PAYMENT" -> {
                Balance now = customerService.recordPayment(who, a.customerId(), a.amountPaise(), "VOICE");
                yield new Done("Recorded " + CustomerService.rupees(a.amountPaise()) + " from " + now.name() + ". Pending is now " + CustomerService.rupees(now.balancePaise()) + ".");
            }
            case "CREDIT" -> {
                Balance now = customerService.giveCredit(who, a.customerId(), a.amountPaise(), "VOICE");
                yield new Done("Gave " + CustomerService.rupees(a.amountPaise()) + " credit to " + now.name() + ". Pending is now " + CustomerService.rupees(now.balancePaise()) + ".");
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown action");
        };
    }

    public void cancel(ShopContext who, String utteranceId) {
        repo.resolve(who.shopId(), utteranceId, "REJECTED");
    }

    public List<AssistantRepository.HistoryItem> history(ShopContext who, int limit) {
        return repo.history(who.shopId(), Math.max(1, Math.min(limit, 100)));
    }

    // ------------------------------------------------------------------ answers (no changes)

    private Outcome sales(String shop, Parsed p) {
        SalesTotals totals = dashboard.salesTotals(shop, from(p.period()), clock.today());
        if (totals.salesPaise() == 0) return Outcome.say("No sales are recorded for " + when(p.period()) + ".");
        return Outcome.say("Sales for " + when(p.period()) + " are " + CustomerService.rupees(totals.salesPaise()) + ".");
    }

    private Outcome profit(String shop, Parsed p) {
        SalesTotals totals = dashboard.salesTotals(shop, from(p.period()), clock.today());
        if (totals.salesPaise() == 0) return Outcome.say("No sales are recorded for " + when(p.period()) + ", so there is no profit to show.");
        long margin = Math.round(totals.profitPaise() * 100.0 / totals.salesPaise());
        return Outcome.say("Gross profit for " + when(p.period()) + " is " + CustomerService.rupees(totals.profitPaise())
                + " (" + margin + "% of sales). This is before shop expenses like rent and wages.");
    }

    private Outcome stockLookup(Parsed p) {
        String lines = p.products().stream().limit(3).map((s) -> s.name() + ": " + stockText(s)).reduce((a, b) -> a + "\n" + b).orElse("");
        return Outcome.say(lines);
    }

    private Outcome balance(Parsed p) {
        String lines = p.people().stream().limit(3).map((c) -> c.name() + " (" + c.phone() + ") "
                + (c.balancePaise() == 0 ? "owes nothing." : "owes " + CustomerService.rupees(c.balancePaise()) + ".")).reduce((a, b) -> a + "\n" + b).orElse("");
        return Outcome.say(lines + (p.people().size() > 3 ? "\n...and " + (p.people().size() - 3) + " more with that name." : ""));
    }

    private Outcome lowStock(String shop) {
        List<StockItem> low = inventory.lowestStock(shop, true, 5);
        if (low.isEmpty()) return Outcome.say("Nothing is running low. All your products are above their reorder level.");
        String list = low.stream().map((s) -> s.name() + (s.status().equals("OUT") ? " (out of stock)" : " (" + fmt(s.stock()) + " left)")).reduce((a, b) -> a + ", " + b).orElse("");
        return Outcome.say(inventory.lowStockCount(shop) + " products need restocking: " + list + ".");
    }

    /** Suggests ordering enough to bring each low product up to twice its reorder level. */
    private Outcome restockList(String shop) {
        List<StockItem> low = inventory.lowestStock(shop, true, 8);
        if (low.isEmpty()) return Outcome.say("Nothing needs restocking right now.");
        String list = low.stream().map((s) -> "• " + s.name() + ": order " + fmt(Math.ceil(Math.max(s.reorderLevel() * 2 - s.stock(), 1))) + " " + s.unit())
                .reduce((a, b) -> a + "\n" + b).orElse("");
        return Outcome.say("Restocking list (enough to reach twice the reorder level):\n" + list);
    }

    private Outcome insights(String shop) {
        LocalDate to = clock.today();
        LocalDate from = to.minusDays(29);
        SalesTotals now = dashboard.salesTotals(shop, from, to);
        SalesTotals before = dashboard.salesTotals(shop, from.minusDays(30), from.minusDays(1));
        StringBuilder out = new StringBuilder();
        if (now.salesPaise() == 0) {
            out.append("• No sales recorded in the last 30 days.");
        } else {
            out.append("• Sales in the last 30 days: ").append(CustomerService.rupees(now.salesPaise()));
            if (before.salesPaise() > 0) {
                long change = Math.round((now.salesPaise() - before.salesPaise()) * 100.0 / before.salesPaise());
                out.append(change >= 0 ? ", up " : ", down ").append(Math.abs(change)).append("% from the 30 days before");
            }
            out.append(".\n• Gross margin: ").append(Math.round(now.profitPaise() * 100.0 / now.salesPaise())).append("%.");
            String best = dashboard.bestProductByProfit(shop, from, to);
            if (best != null) out.append("\n• Your best earner: ").append(best).append(".");
        }
        int low = inventory.lowStockCount(shop);
        if (low > 0) out.append("\n• ").append(low).append(low == 1 ? " product needs" : " products need").append(" restocking.");
        dashboard.topDebtors(shop, 1).stream().findFirst().ifPresent((d) ->
                out.append("\n• Biggest udhaar: ").append(d.name()).append(" owes ").append(CustomerService.rupees(d.pendingPaise())).append("."));
        return Outcome.say(out.toString());
    }

    private Outcome topSeller(String shop, Parsed p) {
        List<ProductSales> sold = advisor.productSales(shop, from(p.period()), clock.today());
        if (sold.isEmpty()) return Outcome.say("Nothing was sold in " + when(p.period()) + ".");
        ProductSales top = sold.get(0);
        String next = sold.stream().skip(1).limit(2).map((s) -> s.name() + " (" + fmt(s.quantity()) + ")").reduce((a, b) -> a + ", " + b).orElse("");
        return Outcome.say("Your top seller in " + when(p.period()) + " is " + top.name() + ": " + fmt(top.quantity()) + " " + top.unit()
                + " sold for " + CustomerService.rupees(top.salesPaise()) + "." + (next.isEmpty() ? "" : " Next: " + next + "."));
    }

    private Outcome bestMargin(String shop, Parsed p) {
        return advisor.productSales(shop, from(p.period()), clock.today()).stream().filter((s) -> s.salesPaise() > 0)
                .max(Comparator.comparingDouble((ProductSales s) -> (s.salesPaise() - s.costPaise()) / (double) s.salesPaise()))
                .map((s) -> Outcome.say("Your best margin in " + when(p.period()) + " is on " + s.name() + ": "
                        + Math.round((s.salesPaise() - s.costPaise()) * 100.0 / s.salesPaise()) + "% (" + CustomerService.rupees(s.salesPaise() - s.costPaise())
                        + " profit on " + CustomerService.rupees(s.salesPaise()) + " of sales)."))
                .orElse(Outcome.say("Nothing was sold in " + when(p.period()) + ", so there is no margin to compare."));
    }

    /** Customers whose udhaar is past its due date, biggest overdue amount first. */
    private Outcome reminders(String shop) {
        LocalDate today = clock.today();
        List<Account> overdue = accounts(shop).stream().filter((a) -> a.pendingPaise() > 0 && a.overduePaise() > 0)
                .sorted(Comparator.comparingLong(Account::overduePaise).reversed()).toList();
        if (overdue.isEmpty()) return Outcome.say("Nobody's payment is overdue right now, so no reminders are needed.");
        String list = overdue.stream().limit(LIST_SIZE).map((a) -> "• " + a.name() + ": " + CustomerService.rupees(a.overduePaise()) + " overdue for "
                + a.daysOverdue(today) + " days (owes " + CustomerService.rupees(a.pendingPaise()) + " in all)").collect(Collectors.joining(NL));
        return Outcome.say(overdue.size() + (overdue.size() == 1 ? " customer needs" : " customers need") + " a payment reminder:" + NL + list
                + more(overdue.size()) + NL + "On the Customer Handling page, Send Reminder prepares the WhatsApp messages.");
    }

    /** Customers who still owe udhaar given more than p.quantity() days ago. */
    private Outcome oldUdhaar(String shop, Parsed p) {
        int days = (int) Math.round(p.quantity());
        LocalDate since = clock.today().minusDays(days);
        List<Account> old = accounts(shop).stream().filter((a) -> a.pendingPaise() > 0 && a.oldestUnpaid() != null && a.oldestUnpaid().isBefore(since))
                .sorted(Comparator.comparing(Account::oldestUnpaid)).toList();
        String age = days % 30 == 0 ? (days / 30) + (days == 30 ? " month" : " months") : days + " days";
        if (old.isEmpty()) return Outcome.say("No customer has unpaid udhaar older than " + age + ".");
        String list = old.stream().limit(LIST_SIZE).map((a) -> "• " + a.name() + ": " + CustomerService.rupees(a.pendingPaise())
                + ", unpaid since " + a.oldestUnpaid().format(DAY)).collect(Collectors.joining(NL));
        return Outcome.say(old.size() + (old.size() == 1 ? " customer has" : " customers have") + " udhaar older than " + age + ":" + NL + list + more(old.size()));
    }

    /** Every payment and udhaar entry of today, the last 7 days or the last 30 days. */
    private Outcome register(String shop, Parsed p) {
        LocalDate from = from(p.period()), to = clock.today();
        Map<String, String> names = customers.people(shop).stream().collect(Collectors.toMap(Person::customerId, Person::name));
        List<Entry> entries = customers.ledger(shop, null).stream().filter((e) -> !e.date().isBefore(from) && !e.date().isAfter(to)).toList();
        if (entries.isEmpty()) return Outcome.say("No payments or udhaar were recorded for " + when(p.period()) + ".");
        long received = 0, given = 0;
        for (Entry e : entries) {
            switch (UdhaarBook.kind(e)) {
                case PAYMENT -> received += e.amountPaise();
                case PAYMENT_REVERSED -> received -= e.amountPaise();
                case CREDIT -> given += e.amountPaise();
                default -> { }
            }
        }
        String list = reversed(entries).stream().limit(LIST_SIZE).map((e) -> "• " + (p.period().equals("today") ? "" : e.date().format(DAY) + ": ")
                + names.get(e.customerId()) + switch (UdhaarBook.kind(e)) {
                    case PAYMENT -> " paid " + CustomerService.rupees(e.amountPaise());
                    case CREDIT -> " took " + CustomerService.rupees(e.amountPaise()) + " udhaar";
                    case WAIVED -> ": " + CustomerService.rupees(e.amountPaise()) + " waived";
                    case PAYMENT_REVERSED -> ": payment of " + CustomerService.rupees(e.amountPaise()) + " undone (entered by mistake)";
                }).collect(Collectors.joining(NL));
        return Outcome.say("Payment register for " + when(p.period()) + ":" + NL + list + more(entries.size())
                + NL + "Received " + CustomerService.rupees(received) + ", udhaar given " + CustomerService.rupees(given) + ".");
    }

    private List<Account> accounts(String shop) {
        return UdhaarBook.accounts(customers.people(shop), customers.ledger(shop, null), null, clock.today());
    }

    private static <T> List<T> reversed(List<T> list) {
        List<T> copy = new java.util.ArrayList<>(list);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private static String more(int total) {
        return total > LIST_SIZE ? NL + "...and " + (total - LIST_SIZE) + " more." : "";
    }

    // ------------------------------------------------------------------ proposals (changes that need a confirm)

    private Outcome stockAdd(Parsed p) {
        if (p.products().isEmpty()) return Outcome.say("Which product? For example: \"10 Chocolate add karo\".");
        if (p.quantity() == null || p.quantity() <= 0) return Outcome.say("How many? For example: \"10 " + p.products().get(0).name() + " add karo\".");
        List<Action> actions = p.products().stream().limit(MAX_OPTIONS).map((s) -> new Action("STOCK_ADD", s.productId(), null, p.quantity(), null,
                "Add " + fmt(p.quantity()) + " " + s.unit() + " to " + s.name() + " (stock " + fmt(s.stock()) + " → " + fmt(s.stock() + p.quantity()) + ")")).toList();
        return new Outcome(actions.size() == 1 ? "Add this stock?" : "Which product did you mean?", actions);
    }

    private Outcome payment(Parsed p) {
        if (p.amountPaise() == null || p.amountPaise() <= 0) return Outcome.say("How much? For example: \"Ramesh ne 500 de diye\".");
        List<Balance> owing = p.people().stream().filter((c) -> c.balancePaise() > 0).toList();
        if (owing.isEmpty()) return Outcome.say(p.people().get(0).name() + " does not owe anything, so there is no payment to record.");
        List<Action> actions = owing.stream().limit(MAX_OPTIONS).filter((c) -> p.amountPaise() <= c.balancePaise()).map((c) -> new Action("PAYMENT", null, c.customerId(), null, p.amountPaise(),
                "Record " + CustomerService.rupees(p.amountPaise()) + " from " + c.name() + " (" + c.phone() + "): pending "
                        + CustomerService.rupees(c.balancePaise()) + " → " + CustomerService.rupees(c.balancePaise() - p.amountPaise()))).toList();
        if (actions.isEmpty()) {
            return Outcome.say(owing.get(0).name() + " owes only " + CustomerService.rupees(owing.get(0).balancePaise())
                    + ", which is less than " + CustomerService.rupees(p.amountPaise()) + ".");
        }
        return new Outcome(actions.size() == 1 ? "Record this payment?" : "Which customer did you mean?", actions);
    }

    private Outcome credit(Parsed p) {
        if (p.amountPaise() == null || p.amountPaise() <= 0) return Outcome.say("How much? For example: \"Ramesh ko 300 ka udhaar diya\".");
        List<Action> actions = p.people().stream().limit(MAX_OPTIONS).map((c) -> new Action("CREDIT", null, c.customerId(), null, p.amountPaise(),
                "Give " + CustomerService.rupees(p.amountPaise()) + " credit to " + c.name() + " (" + c.phone() + "): pending "
                        + CustomerService.rupees(c.balancePaise()) + " → " + CustomerService.rupees(c.balancePaise() + p.amountPaise()))).toList();
        return new Outcome(actions.size() == 1 ? "Give this credit?" : "Which customer did you mean?", actions);
    }

    // ------------------------------------------------------------------ small helpers

    private LocalDate from(String period) {
        return clock.today().minusDays(switch (period) { case "week" -> 6; case "month" -> 29; default -> 0; });
    }

    private static String when(String period) {
        return switch (period) { case "week" -> "the last 7 days"; case "month" -> "the last 30 days"; default -> "today"; };
    }

    private static String stockText(ProductStock s) {
        String status = StockItem.statusOf(s.stock(), s.reorderLevel());
        if (status.equals("OUT")) return "out of stock (reorder level " + fmt(s.reorderLevel()) + ").";
        return fmt(s.stock()) + " " + s.unit() + " left" + (status.equals("LOW") ? ", which is low (reorder level " + fmt(s.reorderLevel()) + ")." : ".");
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    private String stored(String intent, List<Action> actions) {
        try {
            return json.writeValueAsString(new Stored(intent, actions));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Stored read(String saved) {
        try {
            return json.readValue(saved, Stored.class);
        } catch (JsonProcessingException e) {
            // Old sample rows hold a different JSON shape; they never have anything to confirm.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nothing to confirm");
        }
    }
}
