package com.retailer.rewards.config;

import com.retailer.rewards.entity.Customer;
import com.retailer.rewards.entity.Transaction;
import com.retailer.rewards.repository.CustomerRepository;
import com.retailer.rewards.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds the in-memory H2 database on startup with 20 customers and randomized
 * transactions spanning 2024-11-01 through 2026-05-06.
 *
 * Disabled in 'prod' and 'test' profiles.
 */
@Component
@Profile("!prod & !test")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final LocalDate START_DATE = LocalDate.of(2024, 11, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 5, 6);
    private static final long SEED = 42L; // deterministic data across runs

    private static final List<String> CUSTOMER_NAMES = List.of(
            "Alice Johnson", "Bob Smith", "Carol Davis", "David Wilson", "Eve Martinez",
            "Frank Brown", "Grace Lee", "Henry Clark", "Ivy Lewis", "Jack Walker",
            "Kara Hall", "Liam Young", "Mia King", "Noah Wright", "Olivia Scott",
            "Paul Green", "Quinn Adams", "Rachel Baker", "Sam Turner", "Tina Phillips"
    );

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (customerRepository.count() > 0) {
            log.info("DataSeeder: data already present, skipping seed.");
            return;
        }

        Random random = new Random(SEED);
        List<Customer> customers = new ArrayList<>(CUSTOMER_NAMES.size());
        for (String name : CUSTOMER_NAMES) {
            customers.add(customerRepository.save(Customer.builder().name(name).build()));
        }

        long totalDays = ChronoUnit.DAYS.between(START_DATE, END_DATE);
        List<Transaction> transactions = new ArrayList<>();

        for (Customer customer : customers) {
            // Each customer gets 15-40 transactions across the window.
            int txCount = 15 + random.nextInt(26);
            for (int i = 0; i < txCount; i++) {
                LocalDate date = START_DATE.plusDays(random.nextInt((int) totalDays + 1));
                BigDecimal amount = randomAmount(random);
                transactions.add(Transaction.builder()
                        .customer(customer)
                        .amount(amount)
                        .transactionDate(date)
                        .build());
            }
        }

        transactionRepository.saveAll(transactions);

        log.info("DataSeeder: inserted {} customers and {} transactions ({} .. {}).",
                customers.size(), transactions.size(), START_DATE, END_DATE);
    }

    /**
     * Generates a transaction amount weighted across the rewards thresholds:
     *  - 25% below $50  (no points)
     *  - 40% between $50 and $100  (1 pt/$ over $50)
     *  - 35% above $100 (2 pt/$ over $100, 1 pt/$ from $50-$100)
     */
    private static BigDecimal randomAmount(Random random) {
        double roll = random.nextDouble();
        double value;
        if (roll < 0.25) {
            value = 10 + random.nextDouble() * 40;        // 10 .. 50
        } else if (roll < 0.65) {
            value = 50 + random.nextDouble() * 50;        // 50 .. 100
        } else {
            value = 100 + random.nextDouble() * 400;      // 100 .. 500
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
