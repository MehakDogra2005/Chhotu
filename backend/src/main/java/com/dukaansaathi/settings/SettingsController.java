package com.dukaansaathi.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.settings.SettingsRepository.Settings;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final AuthService auth;
    private final SettingsService settings;

    public SettingsController(AuthService auth, SettingsService settings) {
        this.auth = auth;
        this.settings = settings;
    }

    @GetMapping
    public Settings get(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader) {
        return settings.get(auth.requireShop(authHeader));
    }

    /** The shop always comes from the login session, never from the request body. */
    @PutMapping
    public Settings update(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                           @RequestBody Settings body) {
        return settings.update(auth.requireShop(authHeader), body);
    }
}
