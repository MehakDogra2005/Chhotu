package com.dukaansaathi.customers;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.customers.CustomerRepository.Balance;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    record PaymentRequest(long amountPaise) {}

    private final AuthService auth;
    private final CustomerService customers;

    public CustomerController(AuthService auth, CustomerService customers) {
        this.auth = auth;
        this.customers = customers;
    }

    /** "Record Payment": the customer paid amountPaise. The customer must belong to the logged-in shop. */
    @PostMapping("/{customerId}/payments")
    public Map<String, Object> recordPayment(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader,
                                             @PathVariable String customerId, @RequestBody PaymentRequest body) {
        Balance now = customers.recordPayment(auth.requireShop(authHeader), customerId, body.amountPaise(), "UI");
        return Map.of("message", "Recorded " + CustomerService.rupees(body.amountPaise()) + " from " + now.name(),
                "balancePaise", now.balancePaise());
    }
}
