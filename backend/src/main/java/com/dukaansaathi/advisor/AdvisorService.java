package com.dukaansaathi.advisor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dukaansaathi.advisor.AdvisorRepository.Day;
import com.dukaansaathi.advisor.AdvisorRepository.Money;
import com.dukaansaathi.advisor.AdvisorRepository.Price;
import com.dukaansaathi.advisor.AdvisorRepository.ProductSales;
import com.dukaansaathi.advisor.AdvisorResponse.Bucket;
import com.dukaansaathi.advisor.AdvisorResponse.Insight;
import com.dukaansaathi.advisor.AdvisorResponse.Period;
import com.dukaansaathi.advisor.AdvisorResponse.PriceSuggestion;
import com.dukaansaathi.advisor.AdvisorResponse.ProductRow;
import com.dukaansaathi.advisor.AdvisorResponse.Slice;
import com.dukaansaathi.advisor.AdvisorResponse.Spending;
import com.dukaansaathi.advisor.AdvisorResponse.Totals;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.customers.CustomerService;

/** Puts the Business Advisor page together: the period's numbers, the comparison with the period before, and the advice. */
@Service
public class AdvisorService {
    /** Below this margin a product is "low margin", and price suggestions aim for it. */
    public static final double TARGET_MARGIN_PCT = 15;
    private static final int MAX_INSIGHTS = 6;

    private final AdvisorRepository repo;
    private final AppClock clock;

    public AdvisorService(AdvisorRepository repo, AppClock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    /** period is week, month, last-month, quarter or year. */
    public AdvisorResponse build(ShopContext who, String periodKey) {
        String shop = who.shopId();
        Period period = period(periodKey, clock.today());
        LocalDate from = period.from(), to = period.to(), prevFrom = period.compareFrom(), prevTo = period.compareTo();

        Money now = repo.totals(shop, from, to);
        Money before = repo.totals(shop, prevFrom, prevTo);
        Double margin = marginPct(now.profitPaise(), now.salesPaise());
        Double prevMargin = marginPct(before.profitPaise(), before.salesPaise());
        Totals totals = new Totals(now.salesPaise(), changePct(now.salesPaise(), before.salesPaise()),
                now.costPaise(), changePct(now.costPaise(), before.costPaise()),
                now.profitPaise(), changePct(now.profitPaise(), before.profitPaise()),
                margin, margin == null || prevMargin == null ? null : round1(margin - prevMargin));

        List<ProductRow> products = products(repo.productSales(shop, from, to), repo.productSales(shop, prevFrom, prevTo));

        List<Slice> expenses = repo.expensesByCategory(shop, from, to);
        long expensesNow = sum(expenses);
        long expensesBefore = sum(repo.expensesByCategory(shop, prevFrom, prevTo));
        boolean hadBefore = before.salesPaise() > 0 || expensesBefore > 0;
        long bought = repo.stockBought(shop, from, to);
        Spending spending = new Spending(bought, changePct(bought, repo.stockBought(shop, prevFrom, prevTo)),
                expensesNow, changePct(expensesNow, expensesBefore),
                now.profitPaise() - expensesNow, hadBefore ? before.profitPaise() - expensesBefore : null);

        // Where the money from sales went: what the goods cost, then each kind of shop expense
        List<Slice> moneyOut = new ArrayList<>();
        if (now.costPaise() > 0) moneyOut.add(new Slice("Goods sold", now.costPaise()));
        moneyOut.addAll(expenses);

        List<Slice> categories = repo.salesByCategory(shop, from, to);
        return new AdvisorResponse(period, totals, trend(period, repo.daily(shop, from, to)), categories, products, spending,
                moneyOut, repo.purchases(shop, from, to), priceSuggestions(repo.activePrices(shop)),
                insights(period, totals, products, categories, spending));
    }

    /** Price changes that would lift each low-margin product to the target margin, worst margin first. */
    public List<PriceSuggestion> priceSuggestions(List<Price> prices) {
        List<PriceSuggestion> out = new ArrayList<>();
        for (Price p : prices) {
            Double margin = marginPct(p.sellingPricePaise() - p.purchasePricePaise(), p.sellingPricePaise());
            if (p.purchasePricePaise() <= 0 || (margin != null && margin >= TARGET_MARGIN_PCT)) continue;
            // price = cost / (1 - target), rounded up to a whole rupee
            long suggested = (long) Math.ceil(p.purchasePricePaise() / (1 - TARGET_MARGIN_PCT / 100) / 100) * 100;
            if (suggested <= p.sellingPricePaise()) continue;
            out.add(new PriceSuggestion(p.productId(), p.name(), p.purchasePricePaise(), p.sellingPricePaise(), margin,
                    suggested, marginPct(suggested - p.purchasePricePaise(), suggested)));
        }
        out.sort(Comparator.comparing(PriceSuggestion::marginPct, Comparator.nullsFirst(Comparator.naturalOrder())));
        return out;
    }

    // ---- periods

    public static Period period(String key, LocalDate today) {
        return switch (key) {
            case "week" -> new Period(key, today.minusDays(6), today, today.minusDays(13), today.minusDays(7), "day");
            case "month" -> {
                LocalDate from = today.withDayOfMonth(1);
                yield new Period(key, from, today, from.minusMonths(1), today.minusMonths(1), "day"); // the same days last month
            }
            case "last-month" -> {
                LocalDate from = today.withDayOfMonth(1).minusMonths(1);
                LocalDate before = from.minusMonths(1);
                yield new Period(key, from, from.withDayOfMonth(from.lengthOfMonth()), before, before.withDayOfMonth(before.lengthOfMonth()), "day");
            }
            case "quarter" -> new Period(key, today.minusDays(89), today, today.minusDays(179), today.minusDays(90), "week");
            case "year" -> {
                LocalDate from = today.withDayOfYear(1);
                yield new Period(key, from, today, from.minusYears(1), today.minusYears(1), "month");
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown period: " + key);
        };
    }

    /** What the period is compared with, for sentences: "...than last week". */
    private static String comparedWith(String key) {
        return switch (key) {
            case "week" -> "last week";
            case "month" -> "the same days last month";
            case "last-month" -> "the month before";
            case "quarter" -> "the 3 months before";
            default -> "the same time last year";
        };
    }

    /** The period split into days, weeks or months, with 0 where nothing was sold. */
    private static List<Bucket> trend(Period period, List<Day> days) {
        Map<LocalDate, Money> byDay = new HashMap<>();
        days.forEach((d) -> byDay.put(d.date(), d.money()));
        List<Bucket> out = new ArrayList<>();
        for (LocalDate start = period.from(); !start.isAfter(period.to()); ) {
            LocalDate end = switch (period.bucket()) {
                case "week" -> start.plusDays(6);
                case "month" -> start.withDayOfMonth(start.lengthOfMonth());
                default -> start;
            };
            if (end.isAfter(period.to())) end = period.to();
            long sales = 0, cost = 0, profit = 0;
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                Money m = byDay.get(d);
                if (m == null) continue;
                sales += m.salesPaise();
                cost += m.costPaise();
                profit += m.profitPaise();
            }
            out.add(new Bucket(start, end, sales, cost, profit));
            start = end.plusDays(1);
        }
        return out;
    }

    // ---- products

    /** Products sold now, each with its change from the period before. Products that stopped selling are added with 0. */
    private static List<ProductRow> products(List<ProductSales> now, List<ProductSales> before) {
        Map<String, ProductSales> earlier = new LinkedHashMap<>();
        before.forEach((p) -> earlier.put(p.productId(), p));
        List<ProductRow> rows = new ArrayList<>();
        for (ProductSales p : now) {
            ProductSales prev = earlier.remove(p.productId());
            long prevSales = prev == null ? 0 : prev.salesPaise();
            long profit = p.salesPaise() - p.costPaise();
            rows.add(new ProductRow(p.productId(), p.name(), p.category(), p.unit(), p.quantity(), p.salesPaise(), profit,
                    marginPct(profit, p.salesPaise()), prevSales, changePct(p.salesPaise(), prevSales)));
        }
        for (ProductSales prev : earlier.values()) {
            rows.add(new ProductRow(prev.productId(), prev.name(), prev.category(), prev.unit(), 0, 0, 0, null, prev.salesPaise(), -100.0));
        }
        return rows;
    }

    private static long salesGain(ProductRow p) {
        return p.salesPaise() - p.previousSalesPaise();
    }

    // ---- advice

    private static List<Insight> insights(Period period, Totals t, List<ProductRow> products, List<Slice> categories, Spending s) {
        List<Insight> out = new ArrayList<>();
        String vs = comparedWith(period.key());
        if (t.salesPaise() == 0) {
            out.add(new Insight("warn", "No sales are recorded for this period yet."));
            if (s.expensesPaise() > 0) out.add(new Insight("down", "Shop expenses in this period: " + CustomerService.rupees(s.expensesPaise()) + "."));
            return out;
        }

        List<ProductRow> sold = products.stream().filter((p) -> p.salesPaise() > 0).toList();
        // The biggest change in rupees, not in %: a product going from ₹5 to ₹100 is not big news
        sold.stream().filter((p) -> p.salesChangePct() != null && p.salesChangePct() >= 10)
                .max(Comparator.comparingLong(AdvisorService::salesGain))
                .ifPresent((p) -> out.add(new Insight("up", p.salesChangePct() > 200 // "up 1811%" says less than the amounts do
                        ? p.name() + " sales grew from " + CustomerService.rupees(p.previousSalesPaise()) + " to " + CustomerService.rupees(p.salesPaise()) + " compared with " + vs + "."
                        : p.name() + " sales went up " + whole(p.salesChangePct()) + "% compared with " + vs + ".")));

        sold.stream().filter((p) -> p.marginPct() != null && p.marginPct() < TARGET_MARGIN_PCT)
                .min(Comparator.comparing(ProductRow::marginPct))
                .ifPresent((p) -> out.add(new Insight("warn", p.marginPct() < 0
                        ? p.name() + " is selling below cost (" + whole(p.marginPct()) + "% margin). Raise its price."
                        : p.name() + " has a low profit margin (" + whole(p.marginPct()) + "%). Consider a higher price or a cheaper supplier.")));

        if (t.marginChangePoints() != null && Math.abs(t.marginChangePoints()) >= 1) {
            boolean up = t.marginChangePoints() > 0;
            out.add(new Insight(up ? "good" : "down", "Your profit margin " + (up ? "improved" : "fell") + " by "
                    + whole(Math.abs(t.marginChangePoints())) + " points to " + whole(t.marginPct()) + "%."));
        }

        products.stream().filter((p) -> p.salesChangePct() != null && p.salesChangePct() <= -15)
                .min(Comparator.comparingLong(AdvisorService::salesGain))
                .ifPresent((p) -> out.add(new Insight("down", p.salesPaise() == 0
                        ? p.name() + " did not sell at all in this period, but it sold in " + vs + "."
                        : p.name() + " sales dropped " + whole(-p.salesChangePct()) + "% compared with " + vs + ".")));

        if (!categories.isEmpty()) {
            Slice top = categories.get(0);
            out.add(new Insight("star", top.name() + " is your best-selling category (" + whole(top.amountPaise() * 100.0 / t.salesPaise()) + "% of sales)."));
        }

        if (s.expensesPaise() > t.profitPaise()) {
            out.add(new Insight("warn", "Shop expenses (" + CustomerService.rupees(s.expensesPaise()) + ") were more than your gross profit ("
                    + CustomerService.rupees(t.profitPaise()) + "), so the shop lost " + CustomerService.rupees(-s.netProfitPaise()) + "."));
        } else if (s.expensesPaise() > 0) {
            out.add(new Insight("good", "After " + CustomerService.rupees(s.expensesPaise()) + " of expenses, you kept "
                    + CustomerService.rupees(s.netProfitPaise()) + " as net profit."));
        }
        return out.stream().limit(MAX_INSIGHTS).toList();
    }

    // ---- small helpers

    private static long sum(List<Slice> slices) {
        return slices.stream().mapToLong(Slice::amountPaise).sum();
    }

    public static Double changePct(long now, long before) {
        return before == 0 ? null : round1((now - before) * 100.0 / before);
    }

    static Double marginPct(long profit, long sales) {
        return sales == 0 ? null : round1(profit * 100.0 / sales);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static long whole(double value) {
        return Math.round(value);
    }
}
