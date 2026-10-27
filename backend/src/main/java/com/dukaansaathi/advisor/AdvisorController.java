package com.dukaansaathi.advisor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;

@RestController
@RequestMapping("/api/advisor")
public class AdvisorController {
    private final AuthService auth;
    private final AdvisorService advisor;

    public AdvisorController(AuthService auth, AdvisorService advisor) {
        this.auth = auth;
        this.advisor = advisor;
    }

    /** period is week, month, last-month, quarter or year. The shop comes from the login session, not from the request. */
    @GetMapping
    public AdvisorResponse get(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                               @RequestParam(defaultValue = "month") String period) {
        return advisor.build(auth.requireShop(authHeader), period);
    }
}
