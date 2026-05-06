package com.retailer.rewards.config;

import com.retailer.rewards.entity.Customer;
import com.retailer.rewards.entity.Transaction;
import com.retailer.rewards.repository.CustomerRepository;
import com.retailer.rewards.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final List<String> CUSTOMER_NAMES = List.of(
            "Alice Johnson", "Bob Smith", "Carol Davis", "David Wilson", "Eve Martinez",
            "Frank Brown", "Grace Lee", "Henry Clark", "Ivy Lewis", "Jack Walker",
            "Kara Hall", "Liam Young", "Mia King", "Noah Wright", "Olivia Scott",
            "Paul Green", "Quinn Adams", "Rachel Baker", "Sam Turner", "Tina Phillips"
    );

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final long randomSeed;
    private final int minTransactionsPerCustomer;
    private final int maxTransactionsPerCustomer;

    public DataSeeder(CustomerRepository customerRepository,
                      TransactionRepository transactionRepository,
                      @Value("${seeder.start-date:2024-11-01}") LocalDate startDate,
                      @Value("${seeder.end-date:2026-05-06}") LocalDate endDate,
                      @Value("${seeder.random-seed:42}") long randomSeed,
                      @Value("${seeder.min-transactions-per-customer:15}") int minTransactionsPerCustomer,
                      @Value("${seeder.max-transactions-per-customer:40}") int maxTransactionsPerCustomer) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.startDate = startDate;
        this.endDate = endDate;
        this.randomSeed = randomSeed;
        this.minTransactionsPerCustomer = minTransactionsPerCustomer;
        this.maxTransactionsPerCustomer = maxTransactionsPerCustomer;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (customerRepository.count() > 0) {
            log.info("DataSeeder: data already present, skipping seed.");
            return;
        }

        Random random = new Random(randomSeed);
        List<Customer> customers = new ArrayList<>(CUSTOMER_NAMES.size());
        for (String name : CUSTOMER_NAMES) {
            customers.add(customerRepository.save(Customer.builder().name(name).build()));
        }

        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        List<Transaction> transactions = new ArrayList<>();

        int txRange = Math.max(1, maxTransactionsPerCustomer - minTransactionsPerCustomer + 1);
        for (Customer customer : customers) {
            int txCount = minTransactionsPerCustomer + random.nextInt(txRange);
            for (int i = 0; i < txCount; i++) {
                LocalDate date = startDate.plusDays(random.nextInt((int) totalDays + 1));
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
                customers.size(), transactions.size(), startDate, endDate);
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
