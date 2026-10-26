package com.dukaansaathi.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Login (phone code, email code, or Google), shop sign-up, and finding out who a request belongs to. */
@Service
public class AuthService {
    /** name is only filled for Google logins, so the sign-up form can start with it. */
    public record LoginResult(String token, boolean isNewUser, String name) {}

    private record Code(String value, Instant expiresAt) {}

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Code> codes = new ConcurrentHashMap<>();  // phone or email -> pending code
    private final UserRepository users;
    private final SessionStore sessions;
    private final GoogleVerifier google;

    public AuthService(UserRepository users, SessionStore sessions, GoogleVerifier google) {
        this.users = users;
        this.sessions = sessions;
        this.google = google;
    }

    // ---- phone

    public void sendOtp(String phone) {
        checkPhone(phone);
        sendCode(phone);
    }

    public LoginResult verifyOtp(String phone, String code) {
        checkPhone(phone);
        useCode(phone, code);
        return login(users.findIdByPhone(phone).orElseGet(() -> users.create(phone, null, null)), null);
    }

    // ---- email

    public void sendEmailOtp(String email) {
        sendCode(checkEmail(email));
    }

    public LoginResult verifyEmailOtp(String email, String code) {
        String address = checkEmail(email);
        useCode(address, code);
        return login(users.findIdByEmail(address).orElseGet(() -> users.create(null, address, null)), null);
    }

    // ---- Google

    public LoginResult loginWithGoogle(String accessToken) {
        GoogleVerifier.Profile who = google.verify(accessToken);
        return login(users.findIdByEmail(who.email()).orElseGet(() -> users.create(null, who.email(), who.name())), who.name());
    }

    public String googleClientId() {
        return google.clientId();
    }

    // ---- shop and session

    @Transactional
    public void createShop(String authHeader, String ownerName, String shopName, String category, String language) {
        String userId = requireUserId(authHeader);
        if (users.hasShop(userId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Shop already exists");
        if (isBlank(ownerName)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your name");
        if (isBlank(shopName)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your shop name");
        users.createShop(userId, ownerName.trim(), shopName.trim(), category, language);
    }

    /** The logged-in user and their shop; every shop-data request starts here. */
    public ShopContext requireShop(String authHeader) {
        return users.findShopContext(requireUserId(authHeader))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Finish setting up your shop"));
    }

    // ---- helpers

    private LoginResult login(String userId, String name) {
        return new LoginResult(sessions.create(userId), !users.hasShop(userId), name);
    }

    private void sendCode(String phoneOrEmail) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.put(phoneOrEmail, new Code(code, Instant.now().plusSeconds(300)));
        log.info("OTP for {}: {}", phoneOrEmail, code);  // no SMS or email is sent yet: read the code from this log
    }

    private void useCode(String phoneOrEmail, String entered) {
        Code code = codes.remove(phoneOrEmail);  // single use
        if (code == null || Instant.now().isAfter(code.expiresAt()) || !code.value().equals(entered)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP");
        }
    }

    private String requireUserId(String authHeader) {
        return sessions.findUserId(authHeader.replace("Bearer ", ""))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again"));
    }

    private void checkPhone(String phone) {
        if (phone == null || !phone.matches("[6-9]\\d{9}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit mobile number");
        }
    }

    /** Returns the email in lower case, so Rahul@x.com and rahul@x.com are the same person. */
    private String checkEmail(String email) {
        if (email == null || !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        }
        return email.trim().toLowerCase();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
