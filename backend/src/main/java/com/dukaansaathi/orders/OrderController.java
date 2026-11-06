package com.dukaansaathi.orders;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dukaansaathi.auth.AuthService;
import com.dukaansaathi.orders.OrderRepository.OrderRow;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    record NewOrder(String customerId, List<OrderService.Request> lines) {}

    private final AuthService auth;
    private final OrderService orders;

    public OrderController(AuthService auth, OrderService orders) {
        this.auth = auth;
        this.orders = orders;
    }

    @GetMapping
    public List<OrderRow> recent(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader) {
        return orders.recent(auth.requireShop(authHeader));
    }

    /** "Save order" on the Orders screen. */
    @PostMapping
    public OrderService.Saved create(@RequestHeader(value = "Authorization", defaultValue = "") String authHeader, @RequestBody NewOrder body) {
        return orders.create(auth.requireShop(authHeader), body.customerId(), body.lines(), "UI");
    }
}
