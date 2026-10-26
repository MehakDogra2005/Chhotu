package com.dukaansaathi.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;

@Service
public class InventoryService {
    private static final int MAX_ITEMS = 100;
    private static final int ALERTS = 5;

    private final InventoryRepository repo;
    private final AppClock clock;

    public InventoryService(InventoryRepository repo, AppClock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    public InventoryResponse build(ShopContext who, String filter, String search) {
        if (!InventoryRepository.FILTERS.containsKey(filter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown filter: " + filter);
        }
        String shop = who.shopId();
        return new InventoryResponse(
                clock.today(),
                repo.counts(shop),
                repo.items(shop, filter, search, MAX_ITEMS),
                repo.lowestStock(shop, true, ALERTS));
    }
}
