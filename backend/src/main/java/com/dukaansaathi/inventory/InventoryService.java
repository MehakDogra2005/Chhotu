package com.dukaansaathi.inventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.audit.AuditRepository;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.inventory.InventoryRepository.ProductStock;
import com.dukaansaathi.inventory.InventoryRepository.ReportRow;

@Service
public class InventoryService {
    private static final int MAX_ITEMS = 100;
    private static final int ALERTS = 5;

    private static final double MAX_QUANTITY = 100_000;

    private final InventoryRepository repo;
    private final AuditRepository audit;
    private final AppClock clock;

    public InventoryService(InventoryRepository repo, AuditRepository audit, AppClock clock) {
        this.repo = repo;
        this.audit = audit;
        this.clock = clock;
    }

    public ProductStock getProduct(ShopContext who, String productId) {
        return repo.findProduct(who.shopId(), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    /** Stock came in (for example 10 packets arrived). voiceId is the spoken command it came from, or null. */
    @Transactional
    public ProductStock addStock(ShopContext who, String productId, double quantity, String voiceId) {
        ProductStock before = getProduct(who, productId);
        if (quantity <= 0 || quantity > MAX_QUANTITY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a quantity between 1 and " + (long) MAX_QUANTITY);
        }
        double after = before.stock() + quantity;
        repo.addMovement(who.shopId(), productId, clock.today(), "MANUAL_IN", quantity, before.unit(), after,
                voiceId == null ? "MANUAL" : "VOICE_UTTERANCE", voiceId == null ? "MANUAL" : voiceId, "Stock added");
        audit.record(who.shopId(), who.userId(), "inventory_movement", productId, "CREATE",
                Map.of("stock", before.stock()), Map.of("type", "MANUAL_IN", "quantity", quantity),
                voiceId == null ? "UI" : "VOICE");
        return getProduct(who, productId);
    }

    /** Stock value per category and per product (value = stock x purchase price). */
    public StockReport report(ShopContext who) {
        List<ReportRow> rows = repo.reportRows(who.shopId());
        Map<String, StockReport.Category> byCategory = new LinkedHashMap<>();
        for (ReportRow row : rows) {
            StockReport.Category old = byCategory.getOrDefault(row.category(), new StockReport.Category(row.category(), 0, 0, 0));
            byCategory.put(row.category(), new StockReport.Category(row.category(), old.items() + 1,
                    old.needRestock() + (row.status().equals("OK") ? 0 : 1), old.valuePaise() + row.valuePaise()));
        }
        return new StockReport(rows.stream().mapToLong(ReportRow::valuePaise).sum(), List.copyOf(byCategory.values()), rows);
    }

    public InventoryResponse build(ShopContext who, String filter, String search) {
        if (!InventoryRepository.FILTERS.containsKey(filter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown filter: " + filter);
        }
        String shop = who.shopId();
        return new InventoryResponse(
                clock.today(),
                repo.counts(shop),
                repo.items(shop, filter, search, MAX_ITEMS),
                repo.lowestStock(shop, true, ALERTS));
    }
}
