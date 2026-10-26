package com.dukaansaathi.settings;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.settings.SettingsRepository.Settings;

@Service
public class SettingsService {
    private static final Set<String> VOICE_MODES = Set.of("PUSH_TO_TALK", "HANDS_FREE");

    private final SettingsRepository repo;

    public SettingsService(SettingsRepository repo) {
        this.repo = repo;
    }

    public Settings get(ShopContext who) {
        return repo.find(who.userId(), who.shopId());
    }

    /** Saves the shop's profile and preferences. phone and email are not editable here, so they are ignored. */
    @Transactional
    public Settings update(ShopContext who, Settings s) {
        if (isBlank(s.ownerName())) throw bad("Enter your name");
        if (isBlank(s.shopName())) throw bad("Enter your shop name");
        if (isBlank(s.category())) throw bad("Choose a business category");
        if (s.voiceMode() == null || !VOICE_MODES.contains(s.voiceMode())) throw bad("Unknown voice mode");
        if (tooLong(s.ownerName()) || tooLong(s.shopName()) || tooLong(s.city())) throw bad("That text is too long");

        repo.update(who.userId(), who.shopId(), new Settings(s.ownerName().trim(), s.shopName().trim(), s.category().trim(),
                isBlank(s.city()) ? null : s.city().trim(), s.language(), s.voiceMode(),
                s.voiceReplies(), s.lowStockAlerts(), s.paymentReminders(), null, null));
        return get(who);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean tooLong(String s) {
        return s != null && s.length() > 100;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
