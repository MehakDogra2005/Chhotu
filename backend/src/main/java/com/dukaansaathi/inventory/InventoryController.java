package com.dukaansaathi.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final AuthService auth;
    private final InventoryService inventory;

    public InventoryController(AuthService auth, InventoryService inventory) {
        this.auth = auth;
        this.inventory = inventory;
    }

    /** filter is all, low, out or order; search matches product name or category. The shop comes from the login session. */
    @GetMapping
    public InventoryResponse get(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                 @RequestParam(defaultValue = "all") String filter,
                                 @RequestParam(defaultValue = "") String search) {
        return inventory.build(auth.requireShop(authHeader), filter, search);
    }
}
