package com.dukaansaathi.voice;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Stops one shop from using up the paid voice service: at most `max` calls per minute for each shop and kind of call. */
@Component
public class UsageLimit {
    private static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> calls = new ConcurrentHashMap<>();

    public void check(String shopId, String kind, int max) {
        long now = System.currentTimeMillis();
        Deque<Long> times = calls.computeIfAbsent(shopId + "/" + kind, (k) -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) times.pollFirst();
            if (times.size() >= max) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "That is too many voice requests. Wait a minute and try again");
            }
            times.addLast(now);
        }
    }
}
