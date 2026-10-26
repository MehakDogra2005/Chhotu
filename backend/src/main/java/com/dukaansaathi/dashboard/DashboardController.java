package com.dukaansaathi.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final AuthService auth;
    private final DashboardService dashboard;

    public DashboardController(AuthService auth, DashboardService dashboard) {
        this.auth = auth;
        this.dashboard = dashboard;
    }

    /** period is today, week or month. The shop comes from the login session, not from the request. */
    @GetMapping
    public DashboardResponse get(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                 @RequestParam(defaultValue = "month") String period) {
        return dashboard.build(auth.requireShop(authHeader), period);
    }
}
