package com.dukaansaathi.orders;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.audit.AuditRepository;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.customers.CustomerRepository;
import com.dukaansaathi.customers.CustomerRepository.Balance;
import com.dukaansaathi.inventory.InventoryRepository;
import com.dukaansaathi.inventory.InventoryRepository.ProductStock;
import com.dukaansaathi.orders.OrderRepository.NewLine;
import com.dukaansaathi.orders.OrderRepository.OrderRow;

/**
 * Taking a customer's order, from the Orders screen or by voice. Saving an order does NOT take anything out of stock:
 * stock only goes down when the goods are sold (the sale / bill), so an order that is later cancelled costs nothing.
 */
@Service
public class OrderService {
    /** A line the caller asks for. */
    public record Request(String productId, double quantity) {}

    /** What was saved. shortages lists products whose stock is less than the quantity ordered. */
    public record Saved(String orderId, String customerName, List<String> summary, List<String> shortages) {}

    private static final int MAX_LINES = 20;
    private static final double MAX_QUANTITY = 100_000;

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final InventoryRepository inventory;
    private final AuditRepository audit;
    private final AppClock clock;

    public OrderService(OrderRepository orders, CustomerRepository customers, InventoryRepository inventory, AuditRepository audit, AppClock clock) {
        this.orders = orders;
        this.customers = customers;
        this.inventory = inventory;
        this.audit = audit;
        this.clock = clock;
    }

    public List<OrderRow> recent(ShopContext who) {
        return orders.recent(who.shopId(), 30);
    }

    /** Products in the order, merged when the same one is asked for twice, with the quantity checked against stock. */
    public List<String> shortages(String shopId, List<Request> lines) {
        Map<String, ProductStock> products = products(shopId);
        List<String> out = new ArrayList<>();
        for (Request r : merge(lines)) {
            ProductStock p = products.get(r.productId());
            if (p != null && p.stock() < r.quantity()) {
                out.add(p.name() + (p.stock() <= 0 ? " is out of stock" : ": only " + fmt(p.stock()) + " " + p.unit() + " in stock, " + fmt(r.quantity()) + " ordered"));
            }
        }
        return out;
    }

    @Transactional
    public Saved create(ShopContext who, String customerId, List<Request> lines, String source) {
        Balance customer = customers.find(who.shopId(), customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Choose a customer for this order"));
        if (lines == null || lines.isEmpty()) throw bad("Add at least one item to the order");
        if (lines.size() > MAX_LINES) throw bad("An order can have at most " + MAX_LINES + " items");

        Map<String, ProductStock> products = products(who.shopId());
        List<Request> merged = merge(lines);
        List<NewLine> toSave = new ArrayList<>();
        List<String> summary = new ArrayList<>();
        for (Request r : merged) {
            ProductStock p = products.get(r.productId());
            if (p == null) throw bad("One of the products is not in your stock list");
            if (!(r.quantity() > 0) || r.quantity() > MAX_QUANTITY) throw bad("Enter a quantity above 0 for " + p.name());
            toSave.add(new NewLine(p.productId(), p.unit(), r.quantity()));
            summary.add(fmt(r.quantity()) + " " + p.unit() + " " + p.name());
        }

        String id = orders.create(who.shopId(), customer.customerId(), who.userId(), source, toSave, clock.today().atTime(LocalTime.now()));
        audit.record(who.shopId(), who.userId(), "ORDER", id, "CREATE", null, Map.of("customer", customer.name(), "items", summary), source);
        return new Saved(id, customer.name(), summary, shortages(who.shopId(), merged));
    }

    private Map<String, ProductStock> products(String shopId) {
        return inventory.products(shopId).stream().collect(Collectors.toMap(ProductStock::productId, Function.identity(), (a, b) -> a));
    }

    private static List<Request> merge(List<Request> lines) {
        Map<String, Double> total = new LinkedHashMap<>();
        for (Request r : lines) total.merge(r.productId(), r.quantity(), Double::sum);
        return total.entrySet().stream().map((e) -> new Request(e.getKey(), e.getValue())).toList();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
