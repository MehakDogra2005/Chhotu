package com.dukaansaathi.auth;

import java.util.Map;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    record PhoneRequest(String phone) {}
    record VerifyRequest(String phone, String otp) {}
    record EmailRequest(String email) {}
    record EmailVerifyRequest(String email, String otp) {}
    record GoogleRequest(String accessToken) {}
    record ShopRequest(String ownerName, String shopName, String category, String language) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/send-otp")
    public Map<String, String> sendOtp(@RequestBody PhoneRequest req) {
        auth.sendOtp(req.phone());
        return Map.of("message", "OTP sent");
    }

    @PostMapping("/auth/verify-otp")
    public AuthService.LoginResult verifyOtp(@RequestBody VerifyRequest req) {
        return auth.verifyOtp(req.phone(), req.otp());
    }

    @PostMapping("/auth/send-email-otp")
    public Map<String, String> sendEmailOtp(@RequestBody EmailRequest req) {
        auth.sendEmailOtp(req.email());
        return Map.of("message", "OTP sent");
    }

    @PostMapping("/auth/verify-email-otp")
    public AuthService.LoginResult verifyEmailOtp(@RequestBody EmailVerifyRequest req) {
        return auth.verifyEmailOtp(req.email(), req.otp());
    }

    @PostMapping("/auth/google")
    public AuthService.LoginResult google(@RequestBody GoogleRequest req) {
        return auth.loginWithGoogle(req.accessToken());
    }

    /** Lets the login page know whether Google sign-in is set up (empty googleClientId = not set up). */
    @GetMapping("/auth/config")
    public Map<String, String> config() {
        return Map.of("googleClientId", auth.googleClientId());
    }

    @PostMapping("/shops")
    public Map<String, String> createShop(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                          @RequestBody ShopRequest req) {
        auth.createShop(authHeader, req.ownerName(), req.shopName(), req.category(), req.language());
        return Map.of("message", "Shop created");
    }
}
