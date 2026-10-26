package com.dukaansaathi.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Sends the login code by email. Does nothing (and says so) until a mail server is set up in local.properties. */
@Component
public class EmailSender {
    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mail; // only exists once spring.mail.host is set
    private final String from;

    public EmailSender(ObjectProvider<JavaMailSender> mail,
                       @Value("${dukaan.mail-from:}") String from, @Value("${spring.mail.username:}") String username) {
        this.mail = mail;
        this.from = from.isBlank() ? username : from;
    }

    /** Returns true when the email was sent, false when no mail server is set up. Fails if the server refuses it. */
    public boolean send(String to, String code, int validSeconds) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) return false;

        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) message.setFrom(from);
        message.setTo(to);
        message.setSubject("Your Dukaan Saathi code: " + code);
        message.setText("Your Dukaan Saathi login code is " + code + ".\n\nIt works for " + validSeconds
                + " seconds. If you did not ask for it, you can ignore this email.");
        try {
            sender.send(message);
            return true;
        } catch (MailException e) {
            log.warn("Could not send the login email to {}: {}", to, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "We could not send the email. Check the address and try again");
        }
    }
}
