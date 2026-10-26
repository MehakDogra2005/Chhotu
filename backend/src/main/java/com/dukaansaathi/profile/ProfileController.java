package com.dukaansaathi.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.notifications.NotificationService;
import com.dukaansaathi.settings.SettingsRepository;

/** What the top bar needs on every page: who is logged in, their shop, the bell's count, and whether to read answers aloud. */
@RestController
@RequestMapping("/api/me")
public class ProfileController {
    public record Me(String userName, String shopName, String city, int alerts, boolean voiceReplies) {}

    private final AuthService auth;
    private final NotificationService notifications;
    private final SettingsRepository settings;

    public ProfileController(AuthService auth, NotificationService notifications, SettingsRepository settings) {
        this.auth = auth;
        this.notifications = notifications;
        this.settings = settings;
    }

    @GetMapping
    public Me get(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader) {
        ShopContext who = auth.requireShop(authHeader);
        return new Me(who.userName(), who.shopName(), who.city(), notifications.list(who).total(),
                settings.switches(who.shopId()).voiceReplies());
    }
}
