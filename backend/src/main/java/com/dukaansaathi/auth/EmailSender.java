package com.dukaansaathi.auth;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sends the login code by email. Two ways, in this order:
 * 1. Brevo (brevo.api-key / BREVO_API_KEY): the email goes over https, which works on hosts that block the usual mail ports (Render's free plan does).
 * 2. A mail server (spring.mail.host in local.properties, for example Gmail with an app password).
 * With neither set up it does nothing (and says so), and the login code is printed in the server window instead.
 */
@Component
public class EmailSender {
    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mail; // only exists once spring.mail.host is set
    private final String from;
    private final String brevoKey;
    private final String password;
    private final RestClient http = RestClient.create();

    public EmailSender(ObjectProvider<JavaMailSender> mail, @Value("${dukaan.mail-from:}") String from,
                       @Value("${spring.mail.username:}") String username, @Value("${spring.mail.password:}") String password,
                       @Value("${brevo.api-key:}") String brevoKey) {
        this.mail = mail;
        this.password = password.trim();
        this.from = from.isBlank() ? username : from;
        this.brevoKey = brevoKey.trim();
    }

    /** Returns true when the email was sent, false when no way of sending is set up. Fails if the service refuses it. */
    public boolean send(String to, String code, int validSeconds) {
        String subject = "Your Galla code: " + code;
        String text = "Your Galla login code is " + code + ".\n\nIt works for " + validSeconds
                + " seconds. If you did not ask for it, you can ignore this email.";
        if (!brevoKey.isEmpty() && !from.isBlank()) return sendWithBrevo(to, subject, text);

        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null || password.isBlank()) return false; // no mail server, or its password is not filled in yet: the code is printed instead
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
            return true;
        } catch (MailException e) {
            log.warn("Could not send the login email to {}: {}", to, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "We could not send the email. Check the address and try again");
        }
    }

    private boolean sendWithBrevo(String to, String subject, String text) {
        try {
            http.post().uri("https://api.brevo.com/v3/smtp/email").header("api-key", brevoKey)
                    .body(Map.of("sender", Map.of("name", "Galla", "email", from), "to", List.of(Map.of("email", to)),
                            "subject", subject, "textContent", text))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("Brevo could not send the login email to {}: {}", to, e.getMessage()); // the message never holds the key (it travels in a header)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "We could not send the email. Check the address and try again");
        }
    }
}
