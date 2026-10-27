package com.dukaansaathi.customers;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dukaansaathi.customers.CustomerRepository.Entry;
import com.dukaansaathi.customers.CustomerRepository.Person;

/**
 * Works out each customer's account from the credit ledger. Payments clear the oldest udhaar first, so what is
 * still unpaid is always the newest credit; an unpaid credit past its due date is overdue.
 */
public final class UdhaarBook {
    /**
     * paid is repayments (minus repayments that were reversed as a mistake); waived is what the shopkeeper let go
     * (round-offs, discounts). pending = totalCredit - paid - waived. oldestUnpaid is the date of the oldest credit
     * not yet paid off, and overdueSince the due date of the oldest overdue one (both null when nothing is unpaid).
     */
    public record Account(String customerId, String name, String phone, String type,
                          long totalCreditPaise, long paidPaise, long waivedPaise, long pendingPaise,
                          LocalDate firstCredit, LocalDate lastTransaction, LocalDate oldestUnpaid,
                          long overduePaise, LocalDate overdueSince) {
        public Integer daysOverdue(LocalDate today) {
            return overdueSince == null ? null : (int) ChronoUnit.DAYS.between(overdueSince, today);
        }
    }

    private UdhaarBook() {}

    /** Every customer's account, counting ledger rows up to and including upTo (null = all rows). today decides what is overdue. */
    public static List<Account> accounts(List<Person> people, List<Entry> ledger, LocalDate upTo, LocalDate today) {
        Map<String, List<Entry>> byCustomer = new HashMap<>();
        for (Entry e : ledger) {
            if (upTo == null || !e.date().isAfter(upTo)) byCustomer.computeIfAbsent(e.customerId(), (k) -> new ArrayList<>()).add(e);
        }
        return people.stream().map((p) -> account(p, byCustomer.getOrDefault(p.customerId(), List.of()), today)).toList();
    }

    /** Entries come oldest first. */
    static Account account(Person p, List<Entry> entries, LocalDate today) {
        long credit = 0, paid = 0, waived = 0;
        LocalDate first = null, last = null;
        List<Entry> increases = new ArrayList<>();
        for (Entry e : entries) {
            last = e.date();
            switch (kind(e)) {
                case CREDIT -> {
                    credit += e.amountPaise();
                    increases.add(e);
                    if (first == null) first = e.date();
                }
                case PAYMENT -> paid += e.amountPaise();
                case PAYMENT_REVERSED -> paid -= e.amountPaise();
                case WAIVED -> waived += e.amountPaise();
            }
        }

        long cleared = paid + waived;
        LocalDate oldestUnpaid = null, overdueSince = null;
        long overdue = 0;
        for (Entry e : increases) {
            long left = e.amountPaise() - Math.min(Math.max(cleared, 0), e.amountPaise());
            cleared -= e.amountPaise() - left;
            if (left == 0) continue;
            if (oldestUnpaid == null) oldestUnpaid = e.date();
            LocalDate due = dueDate(e);
            if (due.isBefore(today)) {
                overdue += left;
                if (overdueSince == null) overdueSince = due;
            }
        }
        return new Account(p.customerId(), p.name(), p.phone(), p.type(), credit, paid, waived, credit - paid - waived,
                first, last, oldestUnpaid, overdue, overdueSince);
    }

    public enum Kind { CREDIT, PAYMENT, PAYMENT_REVERSED, WAIVED }

    /** What a ledger row means for the balance. An increase that reverses an entry undoes a payment made by mistake. */
    public static Kind kind(Entry e) {
        return switch (e.type()) {
            case "CREDIT_ISSUED" -> Kind.CREDIT;
            case "REPAYMENT" -> Kind.PAYMENT;
            default -> !"INCREASE".equals(e.direction()) ? Kind.WAIVED : e.reverses() != null ? Kind.PAYMENT_REVERSED : Kind.CREDIT;
        };
    }

    static LocalDate dueDate(Entry e) {
        return e.dueDate() != null ? e.dueDate() : e.date().plusDays(CustomerService.DAYS_TO_PAY);
    }
}
