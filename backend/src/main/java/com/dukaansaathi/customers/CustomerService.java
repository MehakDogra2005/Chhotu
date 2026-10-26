package com.dukaansaathi.customers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.audit.AuditRepository;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.customers.CustomerRepository.Balance;

/** The things a shopkeeper can do to a customer's udhaar. Used by the buttons and by voice commands. */
@Service
public class CustomerService {
    private static final long MAX_PAISE = 10_000_000_00L;  // ₹1 crore: anything bigger is surely a typing mistake
    private static final int DAYS_TO_PAY = 30;

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

    /** The customer paid some of what they owe. They cannot pay more than they owe. */
    @Transactional
    public Balance recordPayment(ShopContext who, String customerId, long amountPaise, String source) {
        Balance before = get(who, customerId);
        checkAmount(amountPaise);
        if (amountPaise > before.balancePaise()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    before.name() + " owes only " + rupees(before.balancePaise()) + ", which is less than " + rupees(amountPaise));
        }
        String id = customers.addEntry(who.shopId(), customerId, "REPAYMENT", amountPaise, clock.today(), null, "Payment received");
        audit.record(who.shopId(), who.userId(), "credit_transaction", id, "CREATE",
                Map.of("balance_paise", before.balancePaise()), Map.of("type", "REPAYMENT", "amount_paise", amountPaise), source);
        return get(who, customerId);
    }

    /** The customer took goods on credit. */
    @Transactional
    public Balance giveCredit(ShopContext who, String customerId, long amountPaise, String source) {
        Balance before = get(who, customerId);
        checkAmount(amountPaise);
        String id = customers.addEntry(who.shopId(), customerId, "CREDIT_ISSUED", amountPaise, clock.today(),
                clock.today().plusDays(DAYS_TO_PAY), "Credit given");
        audit.record(who.shopId(), who.userId(), "credit_transaction", id, "CREATE",
                Map.of("balance_paise", before.balancePaise()), Map.of("type", "CREDIT_ISSUED", "amount_paise", amountPaise), source);
        return get(who, customerId);
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
        if (amountPaise <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter an amount more than ₹0");
        if (amountPaise > MAX_PAISE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That amount is too large");
    }
}
