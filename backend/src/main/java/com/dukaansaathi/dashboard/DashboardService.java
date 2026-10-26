package com.dukaansaathi.dashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.inventory.InventoryRepository;
import com.dukaansaathi.dashboard.DashboardRepository.SalesTotals;
import com.dukaansaathi.dashboard.DashboardRepository.UdhaarSummary;
import com.dukaansaathi.dashboard.DashboardResponse.Advisor;
import com.dukaansaathi.dashboard.DashboardResponse.DayTotal;
import com.dukaansaathi.dashboard.DashboardResponse.Period;
import com.dukaansaathi.dashboard.DashboardResponse.Summary;

/** Puts the Home page together: runs the queries and adds the comparisons with the previous period. */
@Service
public class DashboardService {
    private static final Map<String, Integer> PERIOD_DAYS = Map.of("today", 1, "week", 7, "month", 30);
    private static final int TREND_DAYS = 7;

    private final DashboardRepository repo;
    private final InventoryRepository inventory;
    private final AppClock clock;

    public DashboardService(DashboardRepository repo, InventoryRepository inventory, AppClock clock) {
        this.repo = repo;
        this.inventory = inventory;
        this.clock = clock;
    }

    public DashboardResponse build(ShopContext who, String periodKey) {
        Integer days = PERIOD_DAYS.get(periodKey);
        if (days == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown period: " + periodKey);

        String shop = who.shopId();
        LocalDate to = clock.today();
        LocalDate from = to.minusDays(days - 1);
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);

        SalesTotals now = repo.salesTotals(shop, from, to);
        SalesTotals before = repo.salesTotals(shop, prevFrom, prevTo);
        long itemsNow = repo.itemsSold(shop, from, to);
        long itemsBefore = repo.itemsSold(shop, prevFrom, prevTo);
        UdhaarSummary udhaar = repo.udhaarSummary(shop);

        Summary summary = new Summary(now.salesPaise(), changePct(now.salesPaise(), before.salesPaise()),
                itemsNow, changePct(itemsNow, itemsBefore),
                udhaar.totalPaise(), udhaar.customers(), inventory.lowStockCount(shop));

        Double margin = marginPct(now);
        Double prevMargin = marginPct(before);
        Advisor advisor = new Advisor(now.salesPaise(), summary.salesChangePct(),
                now.profitPaise(), changePct(now.profitPaise(), before.profitPaise()),
                margin, margin == null || prevMargin == null ? null : round1(margin - prevMargin),
                trend(shop, to));

        return new DashboardResponse(
                new Period(periodKey, from, to),
                summary,
                repo.recentUtterances(shop, 4),
                inventory.lowestStock(shop, true, 3),
                inventory.lowestStock(shop, false, 4),
                advisor,
                repo.topDebtors(shop, 4),
                repo.recordings(shop, "VOICE_NOTE", 5),
                repo.recordings(shop, "CALL_RECORDING", 5));
    }

    /** Sales for each of the last 7 days, with 0 on days that had no sales. */
    private List<DayTotal> trend(String shop, LocalDate to) {
        LocalDate from = to.minusDays(TREND_DAYS - 1);
        Map<LocalDate, Long> byDay = repo.dailySales(shop, from, to).stream()
                .collect(Collectors.toMap(DayTotal::date, DayTotal::salesPaise));
        List<DayTotal> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.add(new DayTotal(day, byDay.getOrDefault(day, 0L)));
        }
        return days;
    }

    private static Double changePct(long now, long before) {
        return before == 0 ? null : round1((now - before) * 100.0 / before);
    }

    private static Double marginPct(SalesTotals totals) {
        return totals.salesPaise() == 0 ? null : round1(totals.profitPaise() * 100.0 / totals.salesPaise());
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
