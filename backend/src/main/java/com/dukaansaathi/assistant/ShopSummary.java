package com.dukaansaathi.assistant;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.dukaansaathi.advisor.AdvisorRepository;
import com.dukaansaathi.advisor.AdvisorRepository.ProductSales;
import com.dukaansaathi.advisor.AdvisorResponse.Purchase;
import com.dukaansaathi.advisor.AdvisorResponse.Slice;
import com.dukaansaathi.config.AppClock;
import com.dukaansaathi.customers.CustomerRepository;
import com.dukaansaathi.customers.CustomerRepository.Balance;
import com.dukaansaathi.customers.CustomerService;
import com.dukaansaathi.dashboard.DashboardRepository;
import com.dukaansaathi.dashboard.DashboardRepository.SalesTotals;
import com.dukaansaathi.inventory.InventoryRepository;
import com.dukaansaathi.inventory.InventoryRepository.ProductStock;

/**
 * The shop's numbers written as a short piece of plain text, to give Gemini when a question matches none of Munim Ji's ready-made
 * answers. It only ever holds the logged-in shop's data (the shop id comes from the session), and it is capped so it stays small.
 */
@Component
public class ShopSummary {
    private static final int MAX_PRODUCTS = 150;
    private static final int MAX_PEOPLE = 80;

    private final InventoryRepository inventory;
    private final CustomerRepository customers;
    private final DashboardRepository dashboard;
    private final AdvisorRepository advisor;
    private final AppClock clock;

    public ShopSummary(InventoryRepository inventory, CustomerRepository customers, DashboardRepository dashboard, AdvisorRepository advisor, AppClock clock) {
        this.inventory = inventory;
        this.customers = customers;
        this.dashboard = dashboard;
        this.advisor = advisor;
        this.clock = clock;
    }

    public String build(String shop) {
        LocalDate today = clock.today();
        LocalDate month = today.minusDays(29);
        StringBuilder out = new StringBuilder("Today is ").append(today).append(". Money is in rupees.\n");

        out.append("\nSALES (recorded sales, with gross profit):\n");
        out.append(sales("Today", dashboard.salesTotals(shop, today, today)));
        out.append(sales("Yesterday", dashboard.salesTotals(shop, today.minusDays(1), today.minusDays(1))));
        out.append(sales("Last 7 days", dashboard.salesTotals(shop, today.minusDays(6), today)));
        out.append(sales("Last 30 days", dashboard.salesTotals(shop, month, today)));
        out.append(sales("The 30 days before that", dashboard.salesTotals(shop, month.minusDays(30), month.minusDays(1))));

        List<ProductSales> sold = advisor.productSales(shop, month, today);
        out.append("\nBEST SELLING PRODUCTS, last 30 days (quantity, sales, profit):\n");
        sold.stream().limit(8).forEach((s) -> out.append("- ").append(s.name()).append(": ").append(num(s.quantity())).append(' ').append(s.unit())
                .append(", ").append(CustomerService.rupees(s.salesPaise())).append(", profit ").append(CustomerService.rupees(s.salesPaise() - s.costPaise())).append('\n'));

        List<Slice> spent = advisor.expensesByCategory(shop, month, today);
        out.append("\nEXPENSES, last 30 days: ").append(spent.isEmpty() ? "none" : spent.stream()
                .map((s) -> s.name() + " " + CustomerService.rupees(s.amountPaise())).collect(Collectors.joining(", "))).append('\n');

        List<Purchase> bought = advisor.purchases(shop, month, today);
        out.append("\nSTOCK ORDERED FROM SUPPLIERS, last 30 days (date, supplier, status, total, still to pay):\n");
        if (bought.isEmpty()) out.append("- none\n");
        bought.stream().limit(15).forEach((p) -> out.append("- ").append(p.date()).append(", ").append(p.supplier()).append(", ").append(p.status())
                .append(", ").append(CustomerService.rupees(p.totalPaise())).append(", due ").append(CustomerService.rupees(p.duePaise())).append('\n'));

        List<Balance> owing = customers.all(shop).stream().filter((b) -> b.balancePaise() > 0).limit(MAX_PEOPLE).toList();
        out.append("\nUDHAAR (customers who owe money): ").append(dashboard.udhaarSummary(shop).customers()).append(" customers owe ")
                .append(CustomerService.rupees(dashboard.udhaarSummary(shop).totalPaise())).append(" in all\n");
        owing.forEach((b) -> out.append("- ").append(b.name()).append(" owes ").append(CustomerService.rupees(b.balancePaise())).append('\n'));

        List<ProductStock> products = inventory.products(shop);
        out.append("\nPRODUCTS (stock now, reorder level; a product at or below its reorder level needs restocking), ").append(products.size()).append(" in all:\n");
        products.stream().limit(MAX_PRODUCTS).forEach((p) -> out.append("- ").append(p.name()).append(": ").append(num(p.stock())).append(' ').append(p.unit())
                .append(", reorder at ").append(num(p.reorderLevel())).append('\n'));
        return out.toString();
    }

    private static String sales(String label, SalesTotals t) {
        return "- " + label + ": sales " + CustomerService.rupees(t.salesPaise()) + ", gross profit " + CustomerService.rupees(t.profitPaise()) + "\n";
    }

    private static String num(double n) {
        return n == Math.rint(n) ? String.valueOf((long) n) : String.valueOf(Math.round(n * 100) / 100.0);
    }
}
