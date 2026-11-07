package com.dukaansaathi.customers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.audit.AuditRepository;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.customers.CustomerRepository.Balance;
import com.dukaansaathi.inventory.InventoryRepository;
import com.dukaansaathi.inventory.InventoryRepository.ProductDetails;
import com.dukaansaathi.inventory.InventoryRepository.ProductStock;

/**
 * A customer took goods and did not pay. One step records the bill (what they took, at today's prices), takes the goods out of stock, and
 * adds the amount to the customer's udhaar. A customer who is not in the book yet is added first. Either all of it happens or none of it.
 */
@Service
public class CreditSaleService {
    /** One product taken, and how many. */
    public record Line(String productId, double quantity) {}

    /** customer is the account after the bill; items is for example ["2 piece Doodh", "1 pack Pencil"]. */
    public record Done(Balance customer, long totalPaise, List<String> items) {}

    private static final double MAX_QUANTITY = 100_000;
    private static final int MAX_LINES = 30;

    private final CustomerRepository customers;
    private final CustomerService customerService;
    private final InventoryRepository inventory;
    private final AuditRepository audit;
    private final AppClock clock;

    public CreditSaleService(CustomerRepository customers, CustomerService customerService, InventoryRepository inventory, AuditRepository audit, AppClock clock) {
        this.customers = customers;
        this.customerService = customerService;
        this.inventory = inventory;
        this.audit = audit;
        this.clock = clock;
    }

    /** customerId is the existing account; when it is null, newName is added as a new customer. */
    @Transactional
    public Done record(ShopContext who, String customerId, String newName, List<Line> asked, String source) {
        String shop = who.shopId();
        if (asked == null || asked.isEmpty()) throw bad("Say what the customer took");
        // the same product said twice is one line
        Map<String, Double> wanted = new LinkedHashMap<>();
        for (Line l : asked) {
            if (l.quantity() <= 0 || l.quantity() > MAX_QUANTITY) throw bad("Enter a quantity between 1 and " + (long) MAX_QUANTITY);
            wanted.merge(l.productId(), l.quantity(), Double::sum);
        }
        if (wanted.size() > MAX_LINES) throw bad("That is too many different items for one bill");

        // check everything before writing anything
        List<ProductDetails> details = new ArrayList<>();
        List<ProductStock> stocks = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Double> w : wanted.entrySet()) {
            ProductDetails d = inventory.details(shop, w.getKey()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
            ProductStock s = inventory.findProduct(shop, w.getKey()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
            if (d.sellingPricePaise() <= 0) throw bad(d.name() + " has no selling price yet. Set it in Inventory first");
            if (s.stock() < w.getValue()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Only " + trim(s.stock()) + " " + s.unit() + " of " + d.name() + " in stock, not " + trim(w.getValue())
                        + ". Add the stock first");
            }
            details.add(d);
            stocks.add(s);
            total += Math.round(w.getValue() * d.sellingPricePaise());
        }
        if (total <= 0 || total > 10_000_000_00L) throw bad("That bill amount does not look right");

        String id = customerId;
        if (id == null) id = customerService.create(who, newName, null, null, 0).customerId();
        else customerService.get(who, id); // 404 unless it is this shop's customer

        LocalDate today = clock.today();
        long cost = 0;
        for (int i = 0; i < details.size(); i++) cost += Math.round(wanted.get(details.get(i).productId()) * unitCost(details.get(i)));
        String saleId = customers.insertCreditSale(shop, id, today, total, cost);
        List<String> summary = new ArrayList<>();
        for (int i = 0; i < details.size(); i++) {
            ProductDetails d = details.get(i);
            ProductStock s = stocks.get(i);
            double qty = wanted.get(d.productId());
            long amount = Math.round(qty * d.sellingPricePaise());
            String itemId = customers.insertSaleItem(shop, saleId, d.productId(), qty, d.unit(), d.sellingPricePaise(), unitCost(d), amount, Math.round(qty * unitCost(d)));
            inventory.addMovement(shop, d.productId(), today, "SALE_OUT", -qty, d.unit(), s.stock() - qty, "SALE_ITEM", itemId, null);
            summary.add(trim(qty) + " " + d.unit() + " " + d.name());
        }
        String entry = customers.addEntry(shop, id, "CREDIT_ISSUED", total, today, today.plusDays(CustomerService.DAYS_TO_PAY), String.join(", ", summary), saleId);
        audit.record(shop, who.userId(), "sale", saleId, "CREATE", null, Map.of("customer_id", id, "net_amount_paise", total, "payment_method", "Credit"), source);
        audit.record(shop, who.userId(), "credit_transaction", entry, "CREATE", null, Map.of("type", "CREDIT_ISSUED", "amount_paise", total, "sale_id", saleId), source);
        return new Done(customerService.get(who, id), total, summary);
    }

    private static long unitCost(ProductDetails d) {
        return d.purchasePricePaise();
    }

    private static String trim(double n) {
        return n == Math.rint(n) ? String.valueOf((long) n) : String.valueOf(n);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
