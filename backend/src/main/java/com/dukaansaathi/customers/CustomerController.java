package com.dukaansaathi.customers;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.customers.CustomerPageService.Page;
import com.dukaansaathi.customers.CustomerRepository.Balance;
import com.dukaansaathi.customers.CustomerService.History;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    record PaymentRequest(long amountPaise) {}

    record CreditRequest(long amountPaise, String note) {}

    record NewCustomer(String name, String phone, String type, Long openingPaise) {}

    record ReminderRequest(String channel, String message) {}

    private final AuthService auth;
    private final CustomerService customers;
    private final CustomerPageService page;

    public CustomerController(AuthService auth, CustomerService customers, CustomerPageService page) {
        this.auth = auth;
        this.customers = customers;
        this.page = page;
    }

    /** The Customer Handling page. period is week, month, last-month, quarter or year. */
    @GetMapping
    public Page page(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                     @RequestParam(defaultValue = "month") String period) {
        return page.build(auth.requireShop(authHeader), period);
    }

    @PostMapping
    public Balance create(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader, @RequestBody NewCustomer body) {
        return customers.create(auth.requireShop(authHeader), body.name(), body.phone(), body.type(),
                body.openingPaise() == null ? 0 : body.openingPaise());
    }

    @GetMapping("/{customerId}/history")
    public History history(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader, @PathVariable String customerId) {
        return customers.history(auth.requireShop(authHeader), customerId);
    }

    /** "Record Payment": the customer paid amountPaise. The customer must belong to the logged-in shop. */
    @PostMapping("/{customerId}/payments")
    public Map<String, Object> recordPayment(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                             @PathVariable String customerId, @RequestBody PaymentRequest body) {
        Balance now = customers.recordPayment(auth.requireShop(authHeader), customerId, body.amountPaise(), "UI");
        return Map.of("message", "Recorded " + CustomerService.rupees(body.amountPaise()) + " from " + now.name(),
                "balancePaise", now.balancePaise());
    }

    /** "Give Udhaar": the customer took goods worth amountPaise on credit. */
    @PostMapping("/{customerId}/credit")
    public Map<String, Object> giveCredit(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                          @PathVariable String customerId, @RequestBody CreditRequest body) {
        Balance now = customers.giveCredit(auth.requireShop(authHeader), customerId, body.amountPaise(), body.note(), "UI");
        return Map.of("message", "Gave " + CustomerService.rupees(body.amountPaise()) + " udhaar to " + now.name(),
                "balancePaise", now.balancePaise());
    }

    /** The shopkeeper sent a reminder from their phone (WhatsApp or SMS); this notes it down. */
    @PostMapping("/{customerId}/reminders")
    public Map<String, String> reminderSent(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                            @PathVariable String customerId, @RequestBody ReminderRequest body) {
        customers.reminderSent(auth.requireShop(authHeader), customerId, body.channel(), body.message());
        return Map.of("message", "Reminder noted");
    }
}
