package com.dukaansaathi.inventory;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.audit.AuditRepository;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.inventory.InventoryRepository.Movement;
import com.dukaansaathi.inventory.InventoryRepository.ProductDetails;

/** Adding, editing and archiving products. Every change is checked, then written to the audit log. */
@Service
public class ProductService {
    private static final long MAX_PRICE_PAISE = 10_000_000_00L;  // ₹1 crore: anything bigger is surely a typing mistake
    private static final double MAX_QUANTITY = 100_000;
    private static final int HISTORY_LINES = 30;

    private final InventoryRepository repo;
    private final AuditRepository audit;

    public ProductService(InventoryRepository repo, AuditRepository audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public ProductDetails get(ShopContext who, String productId) {
        return repo.details(who.shopId(), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    @Transactional
    public String create(ShopContext who, ProductForm form) {
        ProductForm f = checked(form, true);
        if (repo.nameTaken(who.shopId(), f.name(), "")) throw conflict("You already have a product called " + f.name());
        String id = repo.insertProduct(who.shopId(), f);
        audit.record(who.shopId(), who.userId(), "product", id, "CREATE", null, describe(f), "UI");
        return id;
    }

    @Transactional
    public void update(ShopContext who, String productId, ProductForm form) {
        ProductDetails before = get(who, productId);
        ProductForm f = checked(form, false);
        if (repo.nameTaken(who.shopId(), f.name(), productId)) throw conflict("You already have a product called " + f.name());
        repo.updateProduct(who.shopId(), productId, f);
        audit.record(who.shopId(), who.userId(), "product", productId, "UPDATE", before, describe(f), "UI");
    }

    /** Hides the product but keeps its history. A product waiting for a delivery cannot be archived. */
    @Transactional
    public void archive(ShopContext who, String productId) {
        ProductDetails product = get(who, productId);
        if (repo.isOnOrder(who.shopId(), productId)) {
            throw conflict(product.name() + " is on order. Mark the order as received first");
        }
        repo.archive(who.shopId(), productId);
        audit.record(who.shopId(), who.userId(), "product", productId, "ARCHIVE", Map.of("name", product.name()), null, "UI");
    }

    public List<Movement> history(ShopContext who, String productId) {
        get(who, productId); // 404 unless it is this shop's product
        return repo.movements(who.shopId(), productId, HISTORY_LINES);
    }

    // ------------------------------------------------------------------ checking the form

    /** Trims the text and rejects anything that makes no sense. */
    private ProductForm checked(ProductForm f, boolean adding) {
        if (f == null || isBlank(f.name())) throw bad("Enter the item name");
        if (isBlank(f.category())) throw bad("Enter a category, for example Snacks");
        if (adding && isBlank(f.unit())) throw bad("Choose a unit, for example piece or kg");
        if (f.name().length() > 100 || f.category().length() > 100) throw bad("That text is too long");
        if (f.purchasePricePaise() < 0 || f.purchasePricePaise() > MAX_PRICE_PAISE
                || f.sellingPricePaise() < 0 || f.sellingPricePaise() > MAX_PRICE_PAISE) throw bad("Enter a price between ₹0 and ₹1,00,00,000");
        if (f.reorderLevel() < 0 || f.reorderLevel() > MAX_QUANTITY) throw bad("The reorder level must be 0 or more");
        if (adding && (f.openingStock() < 0 || f.openingStock() > MAX_QUANTITY)) throw bad("The stock you have must be 0 or more");
        return new ProductForm(f.name().trim(), f.category().trim(), adding ? f.unit().trim() : f.unit(),
                f.purchasePricePaise(), f.sellingPricePaise(), f.reorderLevel(), adding ? f.openingStock() : 0);
    }

    private static Map<String, Object> describe(ProductForm f) {
        return Map.of("name", f.name(), "category", f.category(), "purchase_price_paise", f.purchasePricePaise(),
                "selling_price_paise", f.sellingPricePaise(), "reorder_level", f.reorderLevel());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
