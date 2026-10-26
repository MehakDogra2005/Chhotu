package com.dukaansaathi.inventory;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.inventory.InventoryRepository.Movement;
import com.dukaansaathi.inventory.InventoryRepository.ProductDetails;
import com.dukaansaathi.inventory.InventoryRepository.ProductStock;

/** The buttons on the Inventory page: add an item, edit it, add stock, see its history, archive it. */
@RestController
@RequestMapping("/api/products")
public class ProductController {
    record QuantityRequest(double quantity) {}

    private final AuthService auth;
    private final ProductService products;
    private final InventoryService inventory;

    public ProductController(AuthService auth, ProductService products, InventoryService inventory) {
        this.auth = auth;
        this.products = products;
        this.inventory = inventory;
    }

    @PostMapping
    public Map<String, String> create(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                      @RequestBody ProductForm form) {
        String id = products.create(auth.requireShop(authHeader), form);
        return Map.of("productId", id, "message", "Added " + form.name().trim());
    }

    @GetMapping("/{id}")
    public ProductDetails get(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader, @PathVariable String id) {
        return products.get(auth.requireShop(authHeader), id);
    }

    @PutMapping("/{id}")
    public Map<String, String> update(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                      @PathVariable String id, @RequestBody ProductForm form) {
        products.update(auth.requireShop(authHeader), id, form);
        return Map.of("message", "Saved " + form.name().trim());
    }

    @DeleteMapping("/{id}")
    public Map<String, String> archive(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader, @PathVariable String id) {
        products.archive(auth.requireShop(authHeader), id);
        return Map.of("message", "Item archived");
    }

    /** Stock came in. */
    @PostMapping("/{id}/stock")
    public Map<String, Object> addStock(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                        @PathVariable String id, @RequestBody QuantityRequest body) {
        ProductStock now = inventory.addStock(auth.requireShop(authHeader), id, body.quantity(), null);
        return Map.of("message", "Added " + (long) body.quantity() + " " + now.unit() + " to " + now.name(), "stock", now.stock());
    }

    @GetMapping("/{id}/movements")
    public List<Movement> history(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader, @PathVariable String id) {
        return products.history(auth.requireShop(authHeader), id);
    }
}
