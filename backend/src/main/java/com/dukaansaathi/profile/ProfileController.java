package com.dukaansaathi.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.inventory.InventoryRepository;
import com.dukaansaathi.settings.SettingsRepository;

/** What the top bar shows on every page: who is logged in, their shop, and how many alerts there are. */
@RestController
@RequestMapping("/api/me")
public class ProfileController {
    public record Me(String userName, String shopName, String city, int alerts) {}

    private final AuthService auth;
    private final InventoryRepository inventory;
    private final SettingsRepository settings;

    public ProfileController(AuthService auth, InventoryRepository inventory, SettingsRepository settings) {
        this.auth = auth;
        this.inventory = inventory;
        this.settings = settings;
    }

    @GetMapping
    public Me get(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader) {
        ShopContext who = auth.requireShop(authHeader);
        // The shopkeeper can switch low-stock alerts off in Settings; then the bell shows no number.
        int alerts = settings.lowStockAlertsEnabled(who.shopId()) ? inventory.lowStockCount(who.shopId()) : 0;
        return new Me(who.userName(), who.shopName(), who.city(), alerts);
    }
}
