package com.dukaansaathi.auth;

import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Checks an email address before a login code is sent to it: is it written like an address, and can its domain receive mail at all
 * (the domain has a mail server in the DNS)? A made-up domain like "abc@notarealsite123.com" is refused here.
 * What nobody can check from outside is whether that exact mailbox exists (Gmail, for example, does not say), so a mistyped name on a
 * real domain still goes through; the person simply never gets a code.
 */
@Component
public class EmailCheck {
    private static final Logger log = LoggerFactory.getLogger(EmailCheck.class);
    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9._%+'-]{1,64}@([A-Za-z0-9-]{1,63}\\.)+[A-Za-z]{2,24}$");
    private static final Set<String> THROWAWAY = Set.of("mailinator.com", "yopmail.com", "guerrillamail.com", "10minutemail.com", "tempmail.com", "trashmail.com");

    private final Map<String, Boolean> domains = new ConcurrentHashMap<>(); // domain -> can it receive mail (remembered, so a domain is looked up once)

    /** Null when the address looks fine, otherwise what to tell the person. */
    public String problem(String address) {
        if (!FORMAT.matcher(address).matches() || address.contains("..")) return "That email address does not look right. Check it and try again";
        String domain = address.substring(address.indexOf('@') + 1).toLowerCase();
        if (THROWAWAY.contains(domain)) return "Please use your own email address, not a temporary one";
        if (!domains.computeIfAbsent(domain, this::receivesMail)) return "We could not find a mail service for \"" + domain + "\". Check the part after the @";
        return null;
    }

    /** The DNS servers to ask: the computer's own first, then two public ones in case the computer has none Java can use. */
    private static final String[] DNS_SERVERS = {null, "dns://8.8.8.8", "dns://1.1.1.1"};

    /** True when the domain has a mail server (MX) or at least an address (A) in the DNS. If no DNS server answers, the address is allowed. */
    private boolean receivesMail(String domain) {
        for (String server : DNS_SERVERS) {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "2000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            if (server != null) env.put("java.naming.provider.url", server);
            DirContext dns = null;
            try {
                dns = new InitialDirContext(env);
                Attributes found = dns.getAttributes(domain, new String[] {"MX", "A"});
                return found.get("MX") != null || found.get("A") != null;
            } catch (NameNotFoundException e) {
                return false; // the domain does not exist
            } catch (NamingException | RuntimeException e) {
                log.debug("DNS server {} could not look up {}: {}", server, domain, e.getMessage()); // try the next one
            } finally {
                try {
                    if (dns != null) dns.close();
                } catch (NamingException ignored) {
                    // nothing to do
                }
            }
        }
        log.warn("Could not look up the mail server of {} on any DNS server, so the address is allowed", domain);
        return true; // do not block a login only because the lookup failed
    }
}
