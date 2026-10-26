package com.dukaansaathi.restock;

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
import com.dukaansaathi.customers.CustomerService;
import com.dukaansaathi.inventory.InventoryRepository;
import com.dukaansaathi.inventory.InventoryRepository.ProductStock;
import com.dukaansaathi.restock.RestockRepository.Candidate;
import com.dukaansaathi.restock.RestockRepository.OrderLine;
import com.dukaansaathi.restock.RestockRepository.PendingOrder;

/** Working out what to order, placing purchase orders, and receiving them (which adds the stock). */
@Service
public class RestockService {
    /** suggestedQuantity brings the product up to twice its reorder level. */
    public record Suggestion(String productId, String name, String unit, double stock, double reorderLevel,
                             long suggestedQuantity, long unitCostPaise, String supplierName, boolean onOrder) {}

    /** One line of an order the shopkeeper chose. */
    public record Line(String productId, double quantity) {}

    public record PlacedOrder(String supplier, int items, long totalPaise) {}

    public record OrderResult(String message, List<PlacedOrder> orders) {}

    private static final double MAX_QUANTITY = 100_000;
    private static final String NO_SUPPLIER = "UNASSIGNED";

    private final RestockRepository repo;
    private final InventoryRepository inventory;
    private final AuditRepository audit;
    private final AppClock clock;

    public RestockService(RestockRepository repo, InventoryRepository inventory, AuditRepository audit, AppClock clock) {
        this.repo = repo;
        this.inventory = inventory;
        this.audit = audit;
        this.clock = clock;
    }

    /** With no ids: everything that is low or out and not yet ordered. With ids: just those products. */
    public List<Suggestion> suggestions(ShopContext who, List<String> ids) {
        List<Candidate> products = ids == null || ids.isEmpty() ? repo.needed(who.shopId()) : repo.byIds(who.shopId(), ids);
        return products.stream().map((c) -> new Suggestion(c.productId(), c.name(), c.unit(), c.stock(), c.reorderLevel(),
                Math.max(1, (long) Math.ceil(c.reorderLevel() * 2 - c.stock())), c.unitCostPaise(), c.supplierName(), c.onOrder())).toList();
    }

    /** One purchase order per supplier. The products then show as "On Order" until the delivery is received. */
    @Transactional
    public OrderResult placeOrder(ShopContext who, List<Line> lines) {
        if (lines == null || lines.isEmpty()) throw bad("Choose at least one item to order");
        Map<String, Candidate> products = repo.byIds(who.shopId(), lines.stream().map(Line::productId).toList())
                .stream().collect(Collectors.toMap(Candidate::productId, Function.identity()));

        // Check everything first, so nothing is saved if one line is wrong.
        for (Line line : lines) {
            Candidate p = products.get(line.productId());
            if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            if (line.quantity() <= 0 || line.quantity() > MAX_QUANTITY) throw bad("Enter a quantity between 1 and " + (long) MAX_QUANTITY + " for " + p.name());
            if (p.onOrder()) throw new ResponseStatusException(HttpStatus.CONFLICT, p.name() + " is already on order");
        }

        Map<String, List<Line>> bySupplier = new LinkedHashMap<>();
        for (Line line : lines) {
            String supplier = products.get(line.productId()).supplierId();
            bySupplier.computeIfAbsent(supplier == null ? NO_SUPPLIER : supplier, (key) -> new ArrayList<>()).add(line);
        }

        List<PlacedOrder> placed = new ArrayList<>();
        for (Map.Entry<String, List<Line>> group : bySupplier.entrySet()) {
            long total = group.getValue().stream().mapToLong((l) -> Math.round(l.quantity() * products.get(l.productId()).unitCostPaise())).sum();
            String orderId = repo.insertOrder(who.shopId(), group.getKey(), clock.today(), total);
            for (Line l : group.getValue()) {
                Candidate p = products.get(l.productId());
                repo.insertLine(who.shopId(), orderId, p.productId(), l.quantity(), p.unit(), p.unitCostPaise());
            }
            audit.record(who.shopId(), who.userId(), "purchase", orderId, "CREATE", null,
                    Map.of("supplier", group.getKey(), "items", group.getValue().size(), "total_paise", total), "UI");
            String name = products.get(group.getValue().get(0).productId()).supplierName();
            placed.add(new PlacedOrder(name == null ? "No supplier set" : name, group.getValue().size(), total));
        }
        long all = placed.stream().mapToLong(PlacedOrder::totalPaise).sum();
        return new OrderResult("Placed " + placed.size() + (placed.size() == 1 ? " order" : " orders") + " for " + lines.size()
                + (lines.size() == 1 ? " item" : " items") + " (" + CustomerService.rupees(all) + ")", placed);
    }

    /** The delivery came: every item of the order(s) containing this product goes into stock. */
    @Transactional
    public String receive(ShopContext who, String productId) {
        List<PendingOrder> pending = repo.pendingFor(who.shopId(), productId);
        if (pending.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no waiting order for this item");

        int items = 0;
        for (PendingOrder order : pending) {
            for (OrderLine line : repo.lines(order.purchaseId())) {
                ProductStock before = inventory.findProduct(who.shopId(), line.productId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
                inventory.addMovement(who.shopId(), line.productId(), clock.today(), "PURCHASE_IN", line.quantity(), line.unit(),
                        before.stock() + line.quantity(), "PURCHASE_ITEM", line.lineId(), "Order received from " + order.supplierName());
                items++;
            }
            repo.markReceived(who.shopId(), order.purchaseId());
            audit.record(who.shopId(), who.userId(), "purchase", order.purchaseId(), "RECEIVE", Map.of("status", "ORDERED"), Map.of("status", "RECEIVED"), "UI");
        }
        return "Received " + items + (items == 1 ? " item" : " items") + " from " + pending.get(0).supplierName() + ". Your stock is updated.";
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
