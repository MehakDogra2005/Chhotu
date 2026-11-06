package com.dukaansaathi.assistant;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.advisor.AdvisorRepository;
import com.dukaansaathi.advisor.AdvisorRepository.ProductSales;
import com.dukaansaathi.advisor.AdvisorResponse.Period;
import com.dukaansaathi.advisor.AdvisorResponse.Slice;
import com.dukaansaathi.advisor.AdvisorService;
import com.dukaansaathi.assistant.AssistantRepository.Alias;
import com.dukaansaathi.assistant.CommandParser.Intent;
import com.dukaansaathi.assistant.CommandParser.Items;
import com.dukaansaathi.assistant.CommandParser.Parsed;
import com.dukaansaathi.assistant.Context.Want;
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
import com.dukaansaathi.orders.OrderService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The voice assistant's brain. interpret() understands a sentence and either answers it, or proposes a change
 * (add stock, record a payment, give credit). A proposed change is saved on the server and only happens after
 * the shopkeeper confirms it, through execute(). The numbers always come from the database, never from guesses.
 */
@Service
public class AssistantService {
    /** One thing that can be done once confirmed. label is the text on the confirm button; lines is only used by an ORDER. */
    public record Action(String type, String productId, String customerId, Double quantity, Long amountPaise, String label, List<Want> lines) {}

    /** What is being typed into an order, for the Orders screen to show while Saathi takes it. */
    public record Draft(String customerName, List<String> lines) {}

    /**
     * What the browser receives. options is empty for a plain answer; otherwise each option is one action to confirm.
     * navigate is the address of the page to open, or null. context goes back with the next sentence (see Context).
     */
    public record Reply(String utteranceId, String message, List<String> options, String navigate, Context context, Draft draft) {}

    public record Done(String message, String navigate, Context context) {}

    private record Stored(String intent, List<Action> actions) {}

    private record Outcome(String message, List<Action> actions, String navigate, Context context, Draft draft) {
        Outcome(String message, List<Action> actions) {
            this(message, actions, null, null, null);
        }

        static Outcome say(String message) {
            return new Outcome(message, List.of());
        }

        Outcome open(String path) {
            return new Outcome(message, actions, path, context, draft);
        }

        Outcome then(Context next) {
            return new Outcome(message, actions, navigate, next, draft);
        }
    }

    private static final int MAX_TEXT = 300;
    private static final int MAX_OPTIONS = 6;
    private static final int LIST_SIZE = 8;
    private static final String NL = "\n";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM");
    private static final Pattern HINGLISH = Pattern.compile("\\b(ka|ki|ke|hai|karo|batao|ne|ko|diye|diya|kitna|kitni|bacha|aaj|hafte|mahine)\\b");
    private static final String HELP = "Maaf kijiye, main samajh nahi paaya. Aap aise keh sakte hain: \"Chocolate ka stock kitna hai?\", "
            + "\"Kal ki kamai batao\", \"Ramesh ka udhaar kitna hai?\", \"Humein order lene hain\", ya \"10 Chocolate add karo\".";
    private static final Set<Intent> PERIODIC = Set.of(Intent.SALES, Intent.PROFIT, Intent.EXPENSES, Intent.TOP_SELLER, Intent.BEST_MARGIN);
    /** While an order is being taken these mean the shopkeeper changed the subject. */
    private static final Set<Intent> NEW_TOPIC = Set.of(Intent.SALES, Intent.PROFIT, Intent.EXPENSES, Intent.INSIGHTS, Intent.TOP_SELLER,
            Intent.BEST_MARGIN, Intent.LOW_STOCK, Intent.RESTOCK_LIST, Intent.REMINDERS, Intent.OLD_UDHAAR, Intent.PAYMENT_REGISTER,
            Intent.PAYMENT, Intent.CREDIT, Intent.INVENTORY_CHECK);
    private static final int MAX_CHOICES = 4;
    private static final Set<String> PERIODS = Set.of("today", "yesterday", "week", "month");
    /** The order-taking part of the Customer Handling page. */
    static final String ORDERS = "/customers?view=orders";

    private final AssistantRepository repo;
    private final CommandParser parser;
    private final InventoryRepository inventory;
    private final InventoryService inventoryService;
    private final CustomerRepository customers;
    private final CustomerService customerService;
    private final DashboardRepository dashboard;
    private final AdvisorRepository advisor;
    private final OrderService orders;
    private final AppClock clock;
    private final ObjectMapper json;

    public AssistantService(AssistantRepository repo, CommandParser parser, InventoryRepository inventory,
                            InventoryService inventoryService, CustomerRepository customers, CustomerService customerService,
                            DashboardRepository dashboard, AdvisorRepository advisor, OrderService orders, AppClock clock, ObjectMapper json) {
        this.repo = repo;
        this.parser = parser;
        this.inventory = inventory;
        this.inventoryService = inventoryService;
        this.customers = customers;
        this.customerService = customerService;
        this.dashboard = dashboard;
        this.advisor = advisor;
        this.orders = orders;
        this.clock = clock;
        this.json = json;
    }

    // ------------------------------------------------------------------ understanding

    /**
     * via is "gnani" (spoken, heard by the Gnani speech service), "voice" (spoken, heard by the browser) or "typed".
     * sent is the context of the previous reply (null for the first sentence), which lets short follow-ups be understood.
     */
    @Transactional
    public Reply interpret(ShopContext who, String text, String via, Context sent) {
        if (text == null || text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say or type something first");
        if (text.length() > MAX_TEXT) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That is too long. Please keep it short");

        String shop = who.shopId();
        Context ctx = sent == null ? Context.empty() : sent;
        List<ProductStock> products = inventory.products(shop);
        List<Alias> aliases = repo.aliases(shop);
        List<Balance> people = customers.all(shop);
        Parsed p = parser.parse(text, products, aliases, people, ctx);

        // "Aur kal?" asks the same thing again for another day; "aur profit?" asks another thing for the same day
        Intent before = intentOf(ctx.intent());
        if (p.intent() == Intent.UNKNOWN && p.periodGiven() && before != null && PERIODIC.contains(before)) {
            p = new Parsed(before, p.products(), p.people(), p.quantity(), p.amountPaise(), p.period(), true, p.page(), true);
        } else if (PERIODIC.contains(p.intent()) && !p.periodGiven() && p.followUp() && PERIODS.contains(ctx.period())) {
            p = new Parsed(p.intent(), p.products(), p.people(), p.quantity(), p.amountPaise(), ctx.period(), false, p.page(), true);
        }

        Outcome outcome = null;
        if (ctx.ordering()) outcome = ordering(shop, text, p, ctx, products, aliases, people);
        else if (!ctx.choices().isEmpty()) outcome = chosen(text, ctx, people, p);
        else if (Context.INVENTORY.equals(ctx.mode()) && p.products().isEmpty()
                && parser.said(text, "poori", "puri", "pura", "sab", "saari", "saara", "sabhi", "all", "whole", "full", "complete")) {
            outcome = Outcome.say("Ji, poori inventory aapke saamne hai. Kuch aur dekhna ho to bataiye.").open("/inventory");
        }
        if (outcome == null) {
            outcome = switch (p.intent()) {
                case SALES -> sales(shop, p);
                case PROFIT -> profit(shop, p);
                case EXPENSES -> expenses(shop, p);
                case STOCK_LOOKUP -> stockLookup(p);
                case STOCK_ADD -> stockAdd(p);
                case INVENTORY_CHECK -> inventoryCheck();
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
                case ORDER_START -> takeOrder(shop, p, text, Context.empty(), products, aliases, people, true);
                case NAVIGATE -> p.page().path().equals("/inventory") ? inventoryCheck()
                        : Outcome.say("Ji, " + p.page().name() + " khol raha hoon.").open(p.page().path());
                case UNKNOWN -> Outcome.say(notUnderstood(text));
            };
        }

        Context next = outcome.context() != null ? outcome.context() : carry(ctx, p);
        boolean needsConfirm = !outcome.actions().isEmpty();
        String id = repo.save(shop, text.trim(), HINGLISH.matcher(text.toLowerCase()).find() ? "hinglish" : "en",
                "gnani".equals(via) ? "Gnani Prisma" : "voice".equals(via) ? "Browser" : "Typed", stored(p.intent().name(), outcome.actions()),
                p.products().size() == 1 ? p.products().get(0).productId() : null,
                p.products().isEmpty() ? "INTENT_ONLY" : "CATALOGUE_MATCH", needsConfirm ? "PENDING" : "NOT_REQUIRED", clock.today().atTime(LocalTime.now()));
        return new Reply(id, outcome.message(), outcome.actions().stream().map(Action::label).toList(), outcome.navigate(), next, outcome.draft());
    }

    /** A sentence nothing matched. If it was clearly about a customer or a product, say that one was not found. */
    private String notUnderstood(String text) {
        if (parser.said(text, "udhaar", "udhar", "baaki", "balance", "hisaab", "hisab")) {
            return "Mujhe is naam ka koi customer nahi mila. Customer ka naam ek baar phir bataiye, ya Customer Handling mein dekh lijiye.";
        }
        if (parser.said(text, "stock", "bacha", "bache", "kitna", "kitne")) {
            return "Mujhe is naam ka koi product nahi mila. Product ka naam ek baar phir bataiye, ya Inventory mein dekh lijiye.";
        }
        return HELP;
    }

    /** What the conversation is about after this sentence. A sentence that was not understood changes nothing. */
    private static Context carry(Context old, Parsed p) {
        if (p.intent() == Intent.UNKNOWN) return old;
        String product = p.products().size() == 1 ? p.products().get(0).productId() : old.productId();
        String customer = p.people().size() == 1 ? p.people().get(0).customerId() : old.customerId();
        boolean timed = PERIODIC.contains(p.intent()) || p.intent() == Intent.INSIGHTS;
        return new Context(p.intent().name(), timed ? p.period() : old.period(), product, customer, null, List.of(), List.of());
    }

    private static Intent intentOf(String name) {
        try {
            return name == null ? null : Intent.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null; // the browser sent something that is not ours: ignore it
        }
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
                yield new Done("Ho gaya ji. " + now.name() + " mein " + fmt(a.quantity()) + " " + now.unit() + " add kar diye. Ab stock " + fmt(now.stock()) + " hai.",
                        "/inventory?search=" + enc(now.name()), new Context("STOCK_ADD", null, now.productId(), null, null, List.of(), List.of()));
            }
            case "PAYMENT" -> {
                Balance now = customerService.recordPayment(who, a.customerId(), a.amountPaise(), "VOICE");
                yield new Done("Ho gaya ji. " + ji(now.name()) + " se " + CustomerService.rupees(a.amountPaise()) + " mil gaye. Ab " + CustomerService.rupees(now.balancePaise()) + " baaki hai.",
                        customerPage(now), new Context("PAYMENT", null, null, now.customerId(), null, List.of(), List.of()));
            }
            case "CREDIT" -> {
                Balance now = customerService.giveCredit(who, a.customerId(), a.amountPaise(), "VOICE");
                yield new Done("Ho gaya ji. " + ji(now.name()) + " ko " + CustomerService.rupees(a.amountPaise()) + " ka udhaar likh diya. Ab " + CustomerService.rupees(now.balancePaise()) + " baaki hai.",
                        customerPage(now), new Context("CREDIT", null, null, now.customerId(), null, List.of(), List.of()));
            }
            case "ORDER" -> {
                OrderService.Saved done = orders.create(who, a.customerId(),
                        a.lines().stream().map((w) -> new OrderService.Request(w.productId(), w.quantity())).toList(), "VOICE");
                yield new Done("Order save ho gaya ji. " + ji(done.customerName()) + " ke liye: " + String.join(", ", done.summary()) + "."
                        + " Stock abhi kam nahi kiya gaya, woh bill banne par kam hoga."
                        + (done.shortages().isEmpty() ? "" : " Dhyan dein: " + String.join("; ", done.shortages()) + "."),
                        ORDERS + "&customer=" + a.customerId(), new Context("ORDER_DONE", null, null, a.customerId(), null, List.of(), List.of()));
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

    // ------------------------------------------------------------------ taking an order

    /** While an order is being taken every sentence is about the order, unless the shopkeeper clearly changes the subject (then null). */
    private Outcome ordering(String shop, String text, Parsed p, Context ctx, List<ProductStock> products, List<Alias> aliases, List<Balance> people) {
        if (parser.said(text, "rehne do", "rehne de", "cancel", "chhod do", "chod do", "chhodo", "nahi chahiye", "order nahi", "band karo", "ruko", "stop", "koi nahi")) {
            return Outcome.say("Theek hai ji, order nahi liya. Aur kuch madad chahiye to bataiye.")
                    .then(new Context(null, null, null, ctx.customerId(), null, List.of(), List.of()));
        }
        boolean other = NEW_TOPIC.contains(p.intent())
                || (p.intent() == Intent.NAVIGATE && p.page() != CommandParser.ORDERS_PAGE)
                || (p.intent() == Intent.BALANCE && parser.said(text, "udhaar", "udhar", "baaki", "balance", "kitna", "kitni", "hisaab"));
        return other ? null : takeOrder(shop, p, text, ctx, products, aliases, people, false);
    }

    /**
     * One step of taking an order: find the customer, find the items, and when both are known propose saving the order.
     * Nothing is saved until the shopkeeper confirms, and saving an order does not touch stock.
     */
    private Outcome takeOrder(String shop, Parsed p, String text, Context ctx, List<ProductStock> products, List<Alias> aliases,
                              List<Balance> people, boolean first) {
        Map<String, ProductStock> byId = products.stream().collect(Collectors.toMap(ProductStock::productId, (s) -> s, (a, b) -> a));
        Balance customer = people.stream().filter((c) -> c.customerId().equals(ctx.customerId())).findFirst().orElse(null);

        List<Balance> named = parser.people(text, people);
        if (customer == null && !ctx.choices().isEmpty()) {
            customer = parser.pick(text, withIds(people, ctx.choices())); // "pehla wala", "65855"
        }
        if (customer == null && named.size() > 1) return askWhich(named, ctx, "ORDER_START", Context.ORDER);
        if (named.size() == 1 && (customer == null || !customer.customerId().equals(named.get(0).customerId()))) customer = named.get(0);

        Items items = parser.items(text, products, aliases, parser.nameWords(customer != null && named.isEmpty() ? List.of(customer) : named));
        boolean append = !ctx.lines().isEmpty() && (parser.startsWith(text, "aur", "also", "add", "plus", "and") || parser.said(text, "bhi"));
        List<Want> lines = items.found().isEmpty() ? ctx.lines() : append ? concat(ctx.lines(), items.found()) : items.found();
        lines = lines.stream().filter((w) -> byId.containsKey(w.productId())).toList();

        String where = ORDERS + (customer != null ? "&customer=" + customer.customerId() : "");
        String single = lines.size() == 1 ? lines.get(0).productId() : null;
        Context next = new Context("ORDER_START", null, single, customer == null ? null : customer.customerId(), Context.ORDER, List.of(), lines);
        String notFound = items.missing().isEmpty() ? ""
                : customer == null ? " Mujhe \"" + String.join("\", \"", items.missing()) + "\" naam ka koi customer ya samaan nahi mila."
                : " Mujhe \"" + String.join("\", \"", items.missing()) + "\" naam ka samaan nahi mila, isliye use chhod diya.";
        String unclear = items.unclear().isEmpty() ? "" : " " + String.join("; ", items.unclear()) + " mein se kaun sa? Poora naam bata dijiye.";

        if (customer == null) {
            String message = !lines.isEmpty()
                    ? "Ji, samaan note kar liya: " + describe(lines, byId) + "." + notFound + unclear + " Yeh order kis customer ka hai?"
                    : first ? "Bilkul ji! Chaliye, customer ka order lete hain. Aap bataiye, kis customer ka order hai aur kya-kya samaan chahiye?"
                    : "Pehle bataiye, yeh order kis customer ka hai?" + notFound + unclear;
            return new Outcome(message, List.of(), where, next, lines.isEmpty() ? null : new Draft(null, describeEach(lines, byId)));
        }

        String name = ji(customer.name());
        Draft draft = new Draft(customer.name(), describeEach(lines, byId));
        if (lines.isEmpty() || !items.unclear().isEmpty()) {
            boolean problem = !items.missing().isEmpty() || !items.unclear().isEmpty();
            String message = !problem && first ? "Bilkul ji! " + name + " ka order lete hain. Bataiye, unhe kya-kya samaan chahiye?"
                    : !problem ? name + " ka order chal raha hai. Kya-kya samaan chahiye? Jaise: \"do Maggi aur ek kilo cheeni\"."
                    : name + " ka order." + notFound + unclear + (lines.isEmpty() ? " Aap dobara bataiye, kya-kya chahiye?" : "");
            return new Outcome(message, List.of(), where, next, draft);
        }

        List<OrderService.Request> asked = lines.stream().map((w) -> new OrderService.Request(w.productId(), w.quantity())).toList();
        List<String> shortages = orders.shortages(shop, asked);
        String summary = describe(lines, byId);
        Action action = new Action("ORDER", null, customer.customerId(), null, null, "Save order for " + customer.name() + ": " + summary, lines);
        String message = "Ji, main confirm kar leta hoon. " + name + " ke liye: " + summary + "." + notFound
                + (shortages.isEmpty() ? "" : " Dhyan dein: " + String.join("; ", shortages) + ".") + " Kya main order save kar doon?";
        return new Outcome(message, List.of(action), where, next, draft);
    }

    private static List<Want> concat(List<Want> a, List<Want> b) {
        List<Want> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static String describe(List<Want> lines, Map<String, ProductStock> byId) {
        return String.join(", ", describeEach(lines, byId));
    }

    private static List<String> describeEach(List<Want> lines, Map<String, ProductStock> byId) {
        return lines.stream().filter((w) -> byId.containsKey(w.productId()))
                .map((w) -> fmt(w.quantity()) + " " + byId.get(w.productId()).unit() + " " + byId.get(w.productId()).name()).toList();
    }

    // ------------------------------------------------------------------ answers (no changes)

    private Outcome sales(String shop, Parsed p) {
        Period r = range(p.period());
        SalesTotals totals = dashboard.salesTotals(shop, r.from(), r.to());
        Outcome out;
        if (totals.salesPaise() == 0) {
            out = Outcome.say("Ji, " + when(p.period()) + " koi sale record nahi hui.");
        } else {
            out = Outcome.say("Ji, " + when(p.period()) + " ki recorded sale " + CustomerService.rupees(totals.salesPaise()) + " rahi. Yeh bikri hai, munafa nahi: "
                    + "isme se gross profit " + CustomerService.rupees(totals.profitPaise()) + " hai.");
        }
        return out.open("/advisor?period=" + p.period());
    }

    private Outcome profit(String shop, Parsed p) {
        Period r = range(p.period());
        SalesTotals totals = dashboard.salesTotals(shop, r.from(), r.to());
        if (totals.salesPaise() == 0) {
            return Outcome.say("Ji, " + when(p.period()) + " koi sale record nahi hui, isliye munafa dikhane ko kuch nahi hai.").open("/advisor?period=" + p.period());
        }
        long margin = Math.round(totals.profitPaise() * 100.0 / totals.salesPaise());
        return Outcome.say("Ji, " + when(p.period()) + " ka gross profit " + CustomerService.rupees(totals.profitPaise()) + " hai, yaani " + CustomerService.rupees(totals.salesPaise())
                + " ki sale ka " + margin + "%. Yeh shop ke kharche (kiraya, tankhwah wagairah) se pehle ka munafa hai.").open("/advisor?period=" + p.period());
    }

    private Outcome expenses(String shop, Parsed p) {
        Period r = range(p.period());
        List<Slice> spent = advisor.expensesByCategory(shop, r.from(), r.to());
        long total = spent.stream().mapToLong(Slice::amountPaise).sum();
        if (total == 0) return Outcome.say("Ji, " + when(p.period()) + " koi kharcha record nahi hua.").open("/advisor?period=" + p.period());
        String top = spent.stream().limit(3).map((s) -> s.name() + " " + CustomerService.rupees(s.amountPaise())).collect(Collectors.joining(", "));
        return Outcome.say("Ji, " + when(p.period()) + " ke kharche " + CustomerService.rupees(total) + " rahe: " + top + (spent.size() > 3 ? " aur baaki." : ".")).open("/advisor?period=" + p.period());
    }

    private Outcome stockLookup(Parsed p) {
        if (p.products().size() == 1) {
            ProductStock s = p.products().get(0);
            return Outcome.say("Ji, " + s.name() + ": " + stockText(s)).open("/inventory?search=" + enc(s.name()));
        }
        String lines = p.products().stream().limit(3).map((s) -> s.name() + ": " + stockText(s)).reduce((a, b) -> a + "\n" + b).orElse("");
        return Outcome.say(lines);
    }

    private Outcome inventoryCheck() {
        return Outcome.say("Bilkul ji! Chaliye, inventory dekhte hain. Aap kisi particular product ka stock dekhna chahte hain ya poori inventory?")
                .open("/inventory").then(new Context("INVENTORY_CHECK", null, null, null, Context.INVENTORY, List.of(), List.of()));
    }

    private Outcome balance(Parsed p) {
        if (p.people().size() == 1) return balanceOf(p.people().get(0));
        return askWhich(p.people(), Context.empty(), "BALANCE", null);
    }

    private Outcome balanceOf(Balance c) {
        String message = c.balancePaise() == 0 ? "Ji, " + ji(c.name()) + " ka koi udhaar baaki nahi hai."
                : "Ji, " + ji(c.name()) + " ka " + CustomerService.rupees(c.balancePaise()) + " udhaar baaki hai.";
        return Outcome.say(message).open(customerPage(c)).then(new Context("BALANCE", null, null, c.customerId(), null, List.of(), List.of()));
    }

    /** The shopkeeper picked one of the customers Saathi listed. Null when the sentence is something else. */
    private Outcome chosen(String text, Context ctx, List<Balance> people, Parsed p) {
        if (p.intent() != Intent.UNKNOWN && p.intent() != Intent.BALANCE) return null;
        Balance picked = parser.pick(text, withIds(people, ctx.choices()));
        return picked == null ? null : balanceOf(picked);
    }

    /** Several customers share the name: list them (with phone numbers) and ask which one. mode is kept for an order. */
    private Outcome askWhich(List<Balance> matches, Context ctx, String intent, String mode) {
        List<Balance> shown = matches.stream().limit(MAX_CHOICES).toList();
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < shown.size(); i++) {
            Balance c = shown.get(i);
            list.append(i + 1).append(") ").append(c.name()).append(" (").append(c.phone()).append(", ")
                    .append(c.balancePaise() == 0 ? "koi udhaar nahi" : CustomerService.rupees(c.balancePaise()) + " udhaar").append(")").append(NL);
        }
        String first = shown.get(0).name().split(" ")[0];
        Context next = new Context(intent, null, null, null, mode, shown.stream().map(Balance::customerId).toList(), ctx.lines());
        String message = matches.size() + " " + first + " ji mile hain:" + NL + list + "Kaun sa " + first + " ji? Phone ke aakhri digits, ya \"pehla\" / \"dusra\" bol dijiye.";
        return new Outcome(message, List.of(), mode == null ? "/customers?search=" + enc(first) : ORDERS, next, null);
    }

    private static List<Balance> withIds(List<Balance> people, List<String> ids) {
        return people.stream().filter((c) -> ids.contains(c.customerId())).toList();
    }

    private Outcome lowStock(String shop) {
        List<StockItem> low = inventory.lowestStock(shop, true, 5);
        if (low.isEmpty()) return Outcome.say("Nothing is running low. All your products are above their reorder level.");
        String list = low.stream().map((s) -> s.name() + (s.status().equals("OUT") ? " (out of stock)" : " (" + fmt(s.stock()) + " left)")).reduce((a, b) -> a + ", " + b).orElse("");
        return Outcome.say(inventory.lowStockCount(shop) + " products need restocking: " + list + ".").open("/inventory?filter=low");
    }

    /** Suggests ordering enough to bring each low product up to twice its reorder level. */
    private Outcome restockList(String shop) {
        List<StockItem> low = inventory.lowestStock(shop, true, 8);
        if (low.isEmpty()) return Outcome.say("Nothing needs restocking right now.");
        String list = low.stream().map((s) -> "• " + s.name() + ": order " + fmt(Math.ceil(Math.max(s.reorderLevel() * 2 - s.stock(), 1))) + " " + s.unit())
                .reduce((a, b) -> a + "\n" + b).orElse("");
        return Outcome.say("Restocking list (enough to reach twice the reorder level):\n" + list).open("/inventory?restock=1");
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
        return Outcome.say(out.toString()).open("/advisor?period=month");
    }

    private Outcome topSeller(String shop, Parsed p) {
        Period r = range(p.period());
        List<ProductSales> sold = advisor.productSales(shop, r.from(), r.to());
        String page = "/advisor?period=" + p.period();
        if (sold.isEmpty()) return Outcome.say("Ji, " + when(p.period()) + " kuch bika nahi.").open(page);
        ProductSales top = sold.get(0);
        String next = sold.stream().skip(1).limit(2).map((s) -> s.name() + " (" + fmt(s.quantity()) + ")").reduce((a, b) -> a + ", " + b).orElse("");
        return Outcome.say("Ji, " + when(p.period()) + " sabse zyada " + top.name() + " bika: " + fmt(top.quantity()) + " " + top.unit()
                + ", " + CustomerService.rupees(top.salesPaise()) + " ki sale." + (next.isEmpty() ? "" : " Uske baad: " + next + ".")).open(page);
    }

    private Outcome bestMargin(String shop, Parsed p) {
        Period r = range(p.period());
        String page = "/advisor?period=" + p.period();
        return advisor.productSales(shop, r.from(), r.to()).stream().filter((s) -> s.salesPaise() > 0)
                .max(Comparator.comparingDouble((ProductSales s) -> (s.salesPaise() - s.costPaise()) / (double) s.salesPaise()))
                .map((s) -> Outcome.say("Ji, " + when(p.period()) + " sabse achha margin " + s.name() + " par hai: "
                        + Math.round((s.salesPaise() - s.costPaise()) * 100.0 / s.salesPaise()) + "% (" + CustomerService.rupees(s.salesPaise() - s.costPaise())
                        + " ka profit, " + CustomerService.rupees(s.salesPaise()) + " ki sale par).").open(page))
                .orElse(Outcome.say("Ji, " + when(p.period()) + " kuch bika nahi, isliye margin compare nahi ho sakta.").open(page));
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
        LocalDate from = from(p.period()), to = to(p.period());
        Map<String, String> names = customers.people(shop).stream().collect(Collectors.toMap(Person::customerId, Person::name));
        List<Entry> entries = customers.ledger(shop, null).stream().filter((e) -> !e.date().isBefore(from) && !e.date().isAfter(to)).toList();
        if (entries.isEmpty()) return Outcome.say("No payments or udhaar were recorded for " + whenEn(p.period()) + ".");
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
        return Outcome.say("Payment register for " + whenEn(p.period()) + ":" + NL + list + more(entries.size())
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
                "Add " + fmt(p.quantity()) + " " + s.unit() + " to " + s.name() + " (stock " + fmt(s.stock()) + " → " + fmt(s.stock() + p.quantity()) + ")", null)).toList();
        return new Outcome(actions.size() == 1 ? "Add this stock?" : "Which product did you mean?", actions);
    }

    private Outcome payment(Parsed p) {
        if (p.amountPaise() == null || p.amountPaise() <= 0) return Outcome.say("How much? For example: \"Ramesh ne 500 de diye\".");
        List<Balance> owing = p.people().stream().filter((c) -> c.balancePaise() > 0).toList();
        if (owing.isEmpty()) return Outcome.say(p.people().get(0).name() + " does not owe anything, so there is no payment to record.");
        List<Action> actions = owing.stream().limit(MAX_OPTIONS).filter((c) -> p.amountPaise() <= c.balancePaise()).map((c) -> new Action("PAYMENT", null, c.customerId(), null, p.amountPaise(),
                "Record " + CustomerService.rupees(p.amountPaise()) + " from " + c.name() + " (" + c.phone() + "): pending "
                        + CustomerService.rupees(c.balancePaise()) + " → " + CustomerService.rupees(c.balancePaise() - p.amountPaise()), null)).toList();
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
                        + CustomerService.rupees(c.balancePaise()) + " → " + CustomerService.rupees(c.balancePaise() + p.amountPaise()), null)).toList();
        return new Outcome(actions.size() == 1 ? "Give this credit?" : "Which customer did you mean?", actions);
    }

    // ------------------------------------------------------------------ small helpers

    /** The days a period covers: the same ones the Business Advisor page shows for it. */
    private Period range(String period) {
        return AdvisorService.period(period, clock.today());
    }

    private LocalDate from(String period) {
        return clock.today().minusDays(switch (period) { case "yesterday" -> 1; case "week" -> 6; case "month" -> 29; default -> 0; });
    }

    private LocalDate to(String period) {
        return period.equals("yesterday") ? clock.today().minusDays(1) : clock.today();
    }

    /** For a spoken sentence: "Ji, kal ki recorded sale ...". */
    private static String when(String period) {
        return switch (period) { case "yesterday" -> "kal"; case "week" -> "pichhle 7 din"; case "month" -> "is mahine"; default -> "aaj"; };
    }

    private static String whenEn(String period) {
        return switch (period) { case "yesterday" -> "yesterday"; case "week" -> "the last 7 days"; case "month" -> "the last 30 days"; default -> "today"; };
    }

    private static String stockText(ProductStock s) {
        String status = StockItem.statusOf(s.stock(), s.reorderLevel());
        if (status.equals("OUT")) return "stock khatam hai (reorder level " + fmt(s.reorderLevel()) + ").";
        return fmt(s.stock()) + " " + s.unit() + " bache hain" + (status.equals("LOW") ? ", jo kam hai (reorder level " + fmt(s.reorderLevel()) + ")." : ".");
    }

    /** "Ramesh Kumar" becomes "Ramesh ji". */
    private static String ji(String name) {
        return name.trim().split(" ")[0] + " ji";
    }

    private static String enc(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    /** The Customer Handling page, filtered to this customer and with their details open. */
    private static String customerPage(Balance c) {
        return "/customers?search=" + enc(c.name()) + "&open=" + enc(c.customerId());
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
