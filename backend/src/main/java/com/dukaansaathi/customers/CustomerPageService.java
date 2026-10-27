package com.dukaansaathi.customers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.dukaansaathi.advisor.AdvisorResponse.Period;
import com.dukaansaathi.advisor.AdvisorService;
import com.dukaansaathi.auth.ShopContext;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.customers.CustomerRepository.Entry;
import com.dukaansaathi.customers.CustomerRepository.Person;
import com.dukaansaathi.customers.UdhaarBook.Account;
import com.dukaansaathi.customers.UdhaarBook.Kind;
import com.dukaansaathi.recordings.RecordingService;

/** Puts the Customer Handling page together: who owes what, what is overdue, recent entries, reminders and advice. */
@Service
public class CustomerPageService {
    /** Money is paise. Changes compare with the end of (or the whole of) the period before; null when there is nothing to compare. */
    public record Page(Period period, LocalDate today, String shopName, Stats stats, List<Row> customers,
                       List<Txn> transactions, List<Tip> insights, List<RecordingService.Item> recordings) {}

    /** overdue is counted as of today, whatever the period. */
    public record Stats(long outstandingPaise, Double outstandingChangePct, int customersOnUdhaar, int newOnUdhaar,
                        long paymentsPaise, Double paymentsChangePct, int overdueCustomers, long overduePaise) {}

    /** One customer. paidInPeriod: they paid something in the chosen period. lastReminded: the last reminder sent to them. */
    public record Row(String customerId, String name, String phone, String type, long totalCreditPaise, long paidPaise,
                      long waivedPaise, long pendingPaise, LocalDate lastTransaction, long overduePaise, Integer daysOverdue,
                      LocalDate oldestUnpaid, boolean paidInPeriod, LocalDate lastReminded) {}

    /** kind is CREDIT (udhaar given), PAYMENT, WAIVED or PAYMENT_REVERSED (a payment entered by mistake, undone). */
    public record Txn(String id, LocalDate date, String customerId, String customerName, String kind, long amountPaise, String note) {}

    /** kind picks the icon (warn, up, down, good, star, time); action "remind" adds a Send Reminder button. */
    public record Tip(String kind, String text, String action) {}

    private static final int MAX_TRANSACTIONS = 50;
    private static final int MAX_TIPS = 6;
    private static final int OLD_DAYS = 30;

    private final CustomerRepository repo;
    private final RecordingService recordings;
    private final AppClock clock;

    public CustomerPageService(CustomerRepository repo, RecordingService recordings, AppClock clock) {
        this.repo = repo;
        this.recordings = recordings;
        this.clock = clock;
    }

    public Page build(ShopContext who, String periodKey) {
        String shop = who.shopId();
        LocalDate today = clock.today();
        Period period = AdvisorService.period(periodKey, today);
        List<Person> people = repo.people(shop);
        List<Entry> ledger = repo.ledger(shop, null);

        List<Account> now = UdhaarBook.accounts(people, ledger, null, today);
        // At the end of the period ("now" when the period ends today) and at the end of the period before
        List<Account> atEnd = period.to().equals(today) ? now : UdhaarBook.accounts(people, ledger, period.to(), today);
        List<Account> atStart = UdhaarBook.accounts(people, ledger, period.compareTo(), today);

        Map<String, Long> paidNow = payments(ledger, period.from(), period.to());
        long paymentsNow = paidNow.values().stream().mapToLong(Long::longValue).sum();
        long paymentsBefore = payments(ledger, period.compareFrom(), period.compareTo()).values().stream().mapToLong(Long::longValue).sum();
        long outstandingEnd = owed(atEnd), outstandingStart = owed(atStart);
        List<Account> overdue = now.stream().filter((a) -> a.pendingPaise() > 0 && a.overduePaise() > 0).toList();
        Stats stats = new Stats(outstandingEnd, AdvisorService.changePct(outstandingEnd, outstandingStart),
                (int) atEnd.stream().filter((a) -> a.pendingPaise() > 0).count(),
                (int) atEnd.stream().filter((a) -> a.pendingPaise() > 0 && inPeriod(a.firstCredit(), period)).count(),
                paymentsNow, AdvisorService.changePct(paymentsNow, paymentsBefore),
                overdue.size(), overdue.stream().mapToLong(Account::overduePaise).sum());

        Map<String, LocalDate> reminded = repo.lastReminders(shop);
        List<Row> rows = now.stream()
                .sorted(Comparator.comparingLong(Account::pendingPaise).reversed().thenComparing(Account::name))
                .map((a) -> new Row(a.customerId(), a.name(), a.phone(), a.type(), a.totalCreditPaise(), a.paidPaise(), a.waivedPaise(),
                        a.pendingPaise(), a.lastTransaction(), a.overduePaise(), a.pendingPaise() > 0 ? a.daysOverdue(today) : null,
                        a.oldestUnpaid(), paidNow.getOrDefault(a.customerId(), 0L) > 0, reminded.get(a.customerId())))
                .toList();

        Map<String, String> names = people.stream().collect(Collectors.toMap(Person::customerId, Person::name));
        Map<String, String> items = repo.creditSaleItems(shop);
        List<Txn> txns = new ArrayList<>();
        for (int i = ledger.size() - 1; i >= 0 && txns.size() < MAX_TRANSACTIONS; i--) {
            Entry e = ledger.get(i);
            txns.add(new Txn(e.id(), e.date(), e.customerId(), names.get(e.customerId()), UdhaarBook.kind(e).name(), e.amountPaise(), CustomerService.noteOf(e, items)));
        }

        return new Page(period, today, repo.shopName(shop), stats, rows, txns,
                tips(period, today, now, atEnd, atStart, ledger, paidNow, overdue), recordings.latest(shop, 20));
    }

    // ---- advice

    private static List<Tip> tips(Period period, LocalDate today, List<Account> now, List<Account> atEnd, List<Account> atStart,
                                  List<Entry> ledger, Map<String, Long> paidNow, List<Account> overdue) {
        List<Tip> out = new ArrayList<>();
        String when = periodName(period.key());

        if (!overdue.isEmpty()) {
            out.add(new Tip("warn", overdue.size() + (overdue.size() == 1 ? " customer has an overdue payment" : " customers have overdue payments")
                    + " (total " + CustomerService.rupees(overdue.stream().mapToLong(Account::overduePaise).sum()) + "). Send reminders?", "remind"));
        }

        // Whose udhaar grew the most, in rupees
        Map<String, Long> before = atStart.stream().collect(Collectors.toMap(Account::customerId, Account::pendingPaise));
        atEnd.stream().filter((a) -> a.pendingPaise() - before.getOrDefault(a.customerId(), 0L) > 0)
                .max(Comparator.comparingLong((Account a) -> a.pendingPaise() - before.getOrDefault(a.customerId(), 0L)))
                .ifPresent((a) -> {
                    long was = Math.max(before.getOrDefault(a.customerId(), 0L), 0);
                    out.add(new Tip("up", was > 0
                            ? a.name() + "'s udhaar went up " + Math.round((a.pendingPaise() - was) * 100.0 / was) + "% " + when
                                    + " (" + CustomerService.rupees(was) + " → " + CustomerService.rupees(a.pendingPaise()) + ")."
                            : a.name() + " took " + CustomerService.rupees(a.pendingPaise()) + " of new udhaar " + when + ".", null));
                });

        overdue.stream().max(Comparator.comparingLong(Account::overduePaise)).ifPresent((a) ->
                out.add(new Tip("time", a.name() + "'s payment is " + a.daysOverdue(today) + " days overdue (" + CustomerService.rupees(a.overduePaise()) + ").", null)));

        List<Account> old = now.stream().filter((a) -> a.pendingPaise() > 0 && a.oldestUnpaid() != null && a.oldestUnpaid().isBefore(today.minusDays(OLD_DAYS))).toList();
        if (!old.isEmpty()) {
            out.add(new Tip("down", old.size() + (old.size() == 1 ? " customer has" : " customers have") + " udhaar older than 1 month ("
                    + CustomerService.rupees(old.stream().mapToLong(Account::pendingPaise).sum()) + " in all).", null));
        }

        List<String> monthEnd = monthEndPayers(ledger, now);
        if (!monthEnd.isEmpty()) {
            out.add(new Tip("star", monthEnd.size() + (monthEnd.size() == 1 ? " customer usually pays" : " customers usually pay")
                    + " at month-end (" + names(monthEnd) + "). Remind them around the 25th.", null));
        }

        List<String> cleared = now.stream().filter((a) -> a.pendingPaise() <= 0 && paidNow.getOrDefault(a.customerId(), 0L) > 0).map(Account::name).toList();
        if (!cleared.isEmpty()) {
            out.add(new Tip("good", names(cleared) + (cleared.size() == 1 ? " has" : " have") + " cleared all dues " + when + ". Good progress!", null));
        }
        return out.stream().limit(MAX_TIPS).toList();
    }

    /** Customers with at least 2 payments, two thirds of them in the last days of a month (25th to the 3rd). */
    private static List<String> monthEndPayers(List<Entry> ledger, List<Account> accounts) {
        Map<String, int[]> counts = new HashMap<>(); // customer -> [payments, month-end payments]
        for (Entry e : ledger) {
            if (UdhaarBook.kind(e) != Kind.PAYMENT) continue;
            int[] c = counts.computeIfAbsent(e.customerId(), (k) -> new int[2]);
            c[0]++;
            if (e.date().getDayOfMonth() >= 25 || e.date().getDayOfMonth() <= 3) c[1]++;
        }
        Set<String> ids = counts.entrySet().stream().filter((x) -> x.getValue()[0] >= 2 && x.getValue()[1] * 3 >= x.getValue()[0] * 2)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        return accounts.stream().filter((a) -> ids.contains(a.customerId())).map(Account::name).toList();
    }

    // ---- small helpers

    /** customer id -> what they paid between from and to (a payment undone as a mistake is taken off). */
    static Map<String, Long> payments(List<Entry> ledger, LocalDate from, LocalDate to) {
        Map<String, Long> paid = new HashMap<>();
        for (Entry e : ledger) {
            if (e.date().isBefore(from) || e.date().isAfter(to)) continue;
            Kind kind = UdhaarBook.kind(e);
            if (kind == Kind.PAYMENT) paid.merge(e.customerId(), e.amountPaise(), Long::sum);
            if (kind == Kind.PAYMENT_REVERSED) paid.merge(e.customerId(), -e.amountPaise(), Long::sum);
        }
        return paid;
    }

    private static long owed(List<Account> accounts) {
        return accounts.stream().mapToLong((a) -> Math.max(a.pendingPaise(), 0)).sum();
    }

    private static boolean inPeriod(LocalDate day, Period p) {
        return day != null && !day.isBefore(p.from()) && !day.isAfter(p.to());
    }

    /** "Aarav, Isha" or "Aarav, Isha and 3 more" (first names). */
    private static String names(List<String> full) {
        String shown = full.stream().limit(2).map((n) -> n.split(" ")[0]).collect(Collectors.joining(", "));
        return full.size() > 2 ? shown + " and " + (full.size() - 2) + " more" : shown;
    }

    static String periodName(String key) {
        return switch (key) {
            case "week" -> "in the last 7 days";
            case "month" -> "this month";
            case "last-month" -> "last month";
            case "quarter" -> "in the last 3 months";
            default -> "this year";
        };
    }
}
