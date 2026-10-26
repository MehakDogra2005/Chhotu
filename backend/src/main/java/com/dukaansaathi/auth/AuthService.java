package com.dukaansaathi.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Login (phone code, email code, or Google), shop sign-up, and finding out who a request belongs to. */
@Service
public class AuthService {
    /** name is only filled for Google logins, so the sign-up form can start with it. */
    public record LoginResult(String token, boolean isNewUser, String name) {}

    /** delivery is "email" when the code was emailed, or "log" when this server cannot send it yet and printed it instead. */
    public record CodeSent(String message, int expiresInSeconds, String delivery) {}

    /** A code that was sent and has not been used yet. */
    private static class PendingCode {
        final String value;
        final Instant sentAt = Instant.now();
        final AtomicInteger wrongTries = new AtomicInteger();

        PendingCode(String value) {
            this.value = value;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_WRONG_TRIES = 5;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingCode> codes = new ConcurrentHashMap<>();  // phone or email -> its pending code
    private final UserRepository users;
    private final SessionStore sessions;
    private final GoogleVerifier google;
    private final EmailSender email;
    private final int validSeconds;

    public AuthService(UserRepository users, SessionStore sessions, GoogleVerifier google, EmailSender email,
                       @Value("${dukaan.code-valid-seconds:60}") int validSeconds) {
        this.users = users;
        this.sessions = sessions;
        this.google = google;
        this.email = email;
        this.validSeconds = validSeconds;
    }

    // ---- phone

    public CodeSent sendOtp(String phone) {
        checkPhone(phone);
        return sendCode(phone, null);
    }

    public LoginResult verifyOtp(String phone, String code) {
        checkPhone(phone);
        useCode(phone, code);
        return login(users.findIdByPhone(phone).orElseGet(() -> users.create(phone, null, null)), null);
    }

    // ---- email

    public CodeSent sendEmailOtp(String emailAddress) {
        String address = checkEmail(emailAddress);
        return sendCode(address, address);
    }

    /** If the email already has an account the person is logged in to it; a new account is only made for a new email. */
    public LoginResult verifyEmailOtp(String emailAddress, String code) {
        String address = checkEmail(emailAddress);
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

    // ---- login codes

    private LoginResult login(String userId, String name) {
        return new LoginResult(sessions.create(userId), !users.hasShop(userId), name);
    }

    /** Makes a 6-digit code, valid for validSeconds, and sends it. emailAddress is null for a phone number. */
    private CodeSent sendCode(String key, String emailAddress) {
        PendingCode old = codes.get(key);
        if (old != null) { // stops someone asking for code after code (and flooding an inbox)
            long wait = validSeconds / 2 - Duration.between(old.sentAt, Instant.now()).getSeconds();
            if (wait > 0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait " + wait + " seconds before asking for another code");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.put(key, new PendingCode(code));

        String delivery = "log";
        try {
            if (emailAddress != null && email.send(emailAddress, code, validSeconds)) delivery = "email";
        } catch (RuntimeException e) {
            codes.remove(key); // the email failed, so this code is of no use
            throw e;
        }
        if (delivery.equals("log")) log.info("OTP for {}: {}", key, code); // nothing was sent: read the code from this log

        return new CodeSent(delivery.equals("email") ? "We sent a code to your email" : "Code ready", validSeconds, delivery);
    }

    /** Checks the code the person typed. A code works once, only for validSeconds, and only for a few wrong tries. */
    private void useCode(String key, String entered) {
        PendingCode pending = codes.get(key);
        if (pending == null) throw bad("Ask for a code first");
        if (Instant.now().isAfter(pending.sentAt.plusSeconds(validSeconds))) {
            codes.remove(key);
            throw bad("That code has expired. Ask for a new one");
        }
        if (!pending.value.equals(entered)) {
            int tries = pending.wrongTries.incrementAndGet();
            if (tries >= MAX_WRONG_TRIES) {
                codes.remove(key);
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong tries. Ask for a new code");
            }
            int left = MAX_WRONG_TRIES - tries;
            throw bad("Wrong code. " + left + (left == 1 ? " try" : " tries") + " left");
        }
        codes.remove(key); // used
    }

    // ---- helpers

    private String requireUserId(String authHeader) {
        return sessions.findUserId(authHeader.replace("Bearer ", ""))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again"));
    }

    private void checkPhone(String phone) {
        if (phone == null || !phone.matches("[6-9]\\d{9}")) throw bad("Enter a valid 10-digit mobile number");
    }

    /** Returns the email in lower case, so Rahul@x.com and rahul@x.com are the same person. */
    private String checkEmail(String emailAddress) {
        if (emailAddress == null || !emailAddress.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw bad("Enter a valid email address");
        return emailAddress.trim().toLowerCase();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
