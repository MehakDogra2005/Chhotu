package com.dukaansaathi.customers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.audit.AuditRepository;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.customers.CustomerRepository.Balance;
import com.dukaansaathi.customers.CustomerRepository.Entry;

/** The things a shopkeeper can do to a customer's udhaar. Used by the buttons and by voice commands. */
@Service
public class CustomerService {
    /** One line of a customer's history. balanceAfterPaise is what they owed after this line. */
    public record HistoryLine(String id, LocalDate date, String kind, long amountPaise, long balanceAfterPaise, LocalDate dueDate, String note) {}

    public record History(Balance customer, String type, List<HistoryLine> lines) {}

    public static final int DAYS_TO_PAY = 30;
    private static final long MAX_PAISE = 10_000_000_00L;  // ₹1 crore: anything bigger is surely a typing mistake
    private static final Set<String> TYPES = Set.of("Regular", "Retail", "Wholesale");
    private static final Set<String> CHANNELS = Set.of("WHATSAPP", "SMS");

    private final CustomerRepository customers;
    private final AuditRepository audit;
    private final AppClock clock;

    public CustomerService(CustomerRepository customers, AuditRepository audit, AppClock clock) {
        this.customers = customers;
        this.audit = audit;
        this.clock = clock;
    }

    public Balance get(ShopContext who, String customerId) {
        return customers.find(who.shopId(), customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    /** "Add Customer". phone is optional; openingPaise is udhaar they already owe from before (0 for none). */
    @Transactional
    public Balance create(ShopContext who, String name, String phone, String type, long openingPaise) {
        String cleanName = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (cleanName.isEmpty()) throw bad("Enter the customer's name");
        if (cleanName.length() > 100) throw bad("That name is too long");
        String cleanPhone = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        if (cleanPhone.startsWith("+91")) cleanPhone = cleanPhone.substring(3);
        if (!cleanPhone.isEmpty() && !cleanPhone.matches("[6-9]\\d{9}")) throw bad("Enter a valid 10-digit mobile number, or leave it empty");
        if (!cleanPhone.isEmpty() && customers.phoneTaken(who.shopId(), cleanPhone)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A customer with this mobile number already exists");
        }
        if (type != null && !TYPES.contains(type)) throw bad("Pick Regular, Retail or Wholesale");
        if (openingPaise < 0) throw bad("The amount they already owe cannot be negative");
        if (openingPaise > 0) checkAmount(openingPaise);

        String id = customers.create(who.shopId(), cleanName, cleanPhone.isEmpty() ? null : cleanPhone, type == null ? "Regular" : type);
        audit.record(who.shopId(), who.userId(), "customer", id, "CREATE", null, Map.of("name", cleanName), "UI");
        if (openingPaise > 0) {
            String entry = customers.addEntry(who.shopId(), id, "CREDIT_ISSUED", openingPaise, clock.today(), clock.today().plusDays(DAYS_TO_PAY), "Udhaar from before");
            audit.record(who.shopId(), who.userId(), "credit_transaction", entry, "CREATE", null,
                    Map.of("type", "CREDIT_ISSUED", "amount_paise", openingPaise), "UI");
        }
        return get(who, id);
    }

    /** Everything that happened to one customer's udhaar, newest first. */
    public History history(ShopContext who, String customerId) {
        Balance customer = get(who, customerId);
        String type = customers.people(who.shopId()).stream().filter((p) -> p.customerId().equals(customerId)).findFirst().map((p) -> p.type()).orElse(null);
        Map<String, String> items = customers.creditSaleItems(who.shopId());
        List<HistoryLine> lines = new ArrayList<>();
        long balance = 0;
        for (Entry e : customers.ledger(who.shopId(), customerId)) {
            UdhaarBook.Kind kind = UdhaarBook.kind(e);
            balance += switch (kind) { case CREDIT, PAYMENT_REVERSED -> e.amountPaise(); default -> -e.amountPaise(); };
            lines.add(new HistoryLine(e.id(), e.date(), kind.name(), e.amountPaise(), balance,
                    kind == UdhaarBook.Kind.CREDIT ? UdhaarBook.dueDate(e) : null, noteOf(e, items)));
        }
        Collections.reverse(lines);
        return new History(customer, type, lines);
    }

    /** The customer paid some of what they owe. They cannot pay more than they owe. */
    @Transactional
    public Balance recordPayment(ShopContext who, String customerId, long amountPaise, String source) {
        Balance before = get(who, customerId);
        checkAmount(amountPaise);
        if (amountPaise > before.balancePaise()) {
            throw bad(before.name() + " owes only " + rupees(before.balancePaise()) + ", which is less than " + rupees(amountPaise));
        }
        String id = customers.addEntry(who.shopId(), customerId, "REPAYMENT", amountPaise, clock.today(), null, "Payment received");
        audit.record(who.shopId(), who.userId(), "credit_transaction", id, "CREATE",
                Map.of("balance_paise", before.balancePaise()), Map.of("type", "REPAYMENT", "amount_paise", amountPaise), source);
        return get(who, customerId);
    }

    public Balance giveCredit(ShopContext who, String customerId, long amountPaise, String source) {
        return giveCredit(who, customerId, amountPaise, null, source);
    }

    /** The customer took goods on credit. note is what they took ("Atta, oil"), optional. */
    @Transactional
    public Balance giveCredit(ShopContext who, String customerId, long amountPaise, String note, String source) {
        Balance before = get(who, customerId);
        checkAmount(amountPaise);
        String text = note == null || note.isBlank() ? "Credit given" : note.trim();
        if (text.length() > 200) throw bad("Keep the note under 200 letters");
        String id = customers.addEntry(who.shopId(), customerId, "CREDIT_ISSUED", amountPaise, clock.today(),
                clock.today().plusDays(DAYS_TO_PAY), text);
        audit.record(who.shopId(), who.userId(), "credit_transaction", id, "CREATE",
                Map.of("balance_paise", before.balancePaise()), Map.of("type", "CREDIT_ISSUED", "amount_paise", amountPaise), source);
        return get(who, customerId);
    }

    /** Notes that a payment reminder was sent (by WhatsApp or SMS from the shopkeeper's phone), so it is not sent twice. */
    @Transactional
    public void reminderSent(ShopContext who, String customerId, String channel, String message) {
        Balance customer = get(who, customerId);
        if (customer.balancePaise() <= 0) throw bad(customer.name() + " does not owe anything");
        if (!CHANNELS.contains(channel)) throw bad("Unknown channel");
        if (message == null || message.isBlank() || message.length() > 1000) throw bad("Write a reminder message (up to 1000 letters)");
        String id = customers.addReminder(who.shopId(), customerId, customer.balancePaise(), channel, message.trim());
        audit.record(who.shopId(), who.userId(), "payment_reminder", id, "CREATE", null, Map.of("channel", channel), "UI");
    }

    /** What to show in the Notes column: the ledger note, or else what was bought on the bill. */
    static String noteOf(Entry e, Map<String, String> saleItems) {
        if (e.note() != null && !e.note().isBlank()) return e.note();
        return e.saleId() == null ? "" : saleItems.getOrDefault(e.saleId(), "");
    }

    /** 123456 paise becomes ₹1,235 and 10000000 paise becomes ₹1,00,000 (Indian digit grouping). */
    public static String rupees(long paise) {
        String digits = Long.toString(Math.abs(Math.round(paise / 100.0)));
        if (digits.length() > 3) {
            String lastThree = digits.substring(digits.length() - 3);
            digits = digits.substring(0, digits.length() - 3).replaceAll("(\\d)(?=(\\d\\d)+$)", "$1,") + "," + lastThree;
        }
        return (paise < 0 ? "-₹" : "₹") + digits;
    }

    private static void checkAmount(long amountPaise) {
        if (amountPaise <= 0) throw bad("Enter an amount more than ₹0");
        if (amountPaise > MAX_PAISE) throw bad("That amount is too large");
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
