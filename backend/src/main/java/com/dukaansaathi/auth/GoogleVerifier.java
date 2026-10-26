package com.dukaansaathi.auth;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Checks a Google access token with Google, and returns who it belongs to.
 * Needs google.client-id (set the GOOGLE_CLIENT_ID environment variable); see the README.
 */
@Component
public class GoogleVerifier {
    public record Profile(String email, String name) {}

    private final String clientId;
    private final RestClient http = RestClient.create();

    public GoogleVerifier(@Value("${google.client-id:}") String clientId) {
        this.clientId = clientId;
    }

    /** Shown to the browser so it can start the Google popup. Empty means Google login is not set up. */
    public String clientId() {
        return clientId;
    }

    public Profile verify(String accessToken) {
        if (clientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not set up yet");
        }
        try {
            // 1. Was this token issued to our app (and not to some other website)?
            Map<?, ?> token = http.get().uri("https://oauth2.googleapis.com/tokeninfo?access_token={t}", accessToken)
                    .retrieve().body(Map.class);
            if (token == null || !clientId.equals(token.get("aud"))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This Google login was not meant for Dukaan Saathi");
            }
            // 2. Who is it?
            Map<?, ?> user = http.get().uri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer " + accessToken).retrieve().body(Map.class);
            if (user == null || !Boolean.TRUE.equals(user.get("email_verified"))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your Google email is not verified");
            }
            return new Profile(((String) user.get("email")).toLowerCase(), (String) user.get("name"));
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google login failed. Please try again");
        }
    }
}
