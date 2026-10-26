package com.dukaansaathi.restock;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;

@RestController
@RequestMapping("/api/restock")
public class RestockController {
    record OrderRequest(List<RestockService.Line> lines) {}

    private final AuthService auth;
    private final RestockService restock;

    public RestockController(AuthService auth, RestockService restock) {
        this.auth = auth;
        this.restock = restock;
    }

    /** "Generate Restocking List". ids (comma separated) limits it to the products you ticked. */
    @GetMapping
    public List<RestockService.Suggestion> suggestions(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                                       @RequestParam(required = false) String ids) {
        List<String> chosen = ids == null || ids.isBlank() ? null : Arrays.asList(ids.split(","));
        return restock.suggestions(auth.requireShop(authHeader), chosen);
    }

    /** "Place order". */
    @PostMapping("/orders")
    public RestockService.OrderResult placeOrder(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                                 @RequestBody OrderRequest body) {
        return restock.placeOrder(auth.requireShop(authHeader), body.lines());
    }

    /** "Mark order received": the waiting order for this product has arrived. */
    @PostMapping("/receive/{productId}")
    public Map<String, String> receive(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                       @PathVariable String productId) {
        return Map.of("message", restock.receive(auth.requireShop(authHeader), productId));
    }
}
