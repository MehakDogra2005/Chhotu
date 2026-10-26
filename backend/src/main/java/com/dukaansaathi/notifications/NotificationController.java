package com.dukaansaathi.notifications;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.notifications.NotificationService.Notifications;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final AuthService auth;
    private final NotificationService notifications;

    public NotificationController(AuthService auth, NotificationService notifications) {
        this.auth = auth;
        this.notifications = notifications;
    }

    @GetMapping
    public Notifications get(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader) {
        return notifications.list(auth.requireShop(authHeader));
    }
}
