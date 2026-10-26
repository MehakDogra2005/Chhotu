package com.dukaansaathi.auth;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Login sessions (token -> user id). In memory for now; replace this class with Redis or a table to run several servers. */
@Component
public class SessionStore {
    private final Map<String, String> userByToken = new ConcurrentHashMap<>();

    public String create(String userId) {
        String token = UUID.randomUUID().toString();
        userByToken.put(token, userId);
        return token;
    }

    public Optional<String> findUserId(String token) {
        return Optional.ofNullable(userByToken.get(token));
    }
}
