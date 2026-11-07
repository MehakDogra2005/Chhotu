package com.dukaansaathi.translations;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.auth.ShopContext;

/** The shop's product and customer names in the page language, for the page to swap in. */
@RestController
@RequestMapping("/api/translations")
public class TranslationController {
    private final AuthService auth;
    private final NameTranslations names;

    public TranslationController(AuthService auth, NameTranslations names) {
        this.auth = auth;
        this.names = names;
    }

    @GetMapping
    public Map<String, String> names(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader, @RequestParam String language) {
        ShopContext who = auth.requireShop(authHeader);
        if (!language.matches("[a-z]{2}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown language");
        return names.forShop(who.shopId(), language);
    }
}
