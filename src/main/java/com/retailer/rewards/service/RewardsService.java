package com.retailer.rewards.service;

import com.retailer.rewards.entity.Transaction;
import com.retailer.rewards.dto.CustomerRewardsResponse;
import com.retailer.rewards.exception.CustomerNotFoundException;
import com.retailer.rewards.repository.CustomerRepository;
import com.retailer.rewards.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RewardsService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int DEFAULT_WINDOW_MONTHS = 3;

    private final TransactionRepository transactionRepository;

    private final CustomerRepository customerRepository;

    /**
     * Returns rewards summary for all customers for the given date range.
     * If the caller doesn't pass any dates, then last three months rewards will be shared by default.
     */
    @Transactional(readOnly = true)
    public List<CustomerRewardsResponse> getRewards(LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveRange(startDate, endDate);
        log.debug("Fetching rewards for all customers between {} and {}", dateRange.start(), dateRange.end());
        List<Transaction> transactions = transactionRepository.findInRange(dateRange.start(), dateRange.end());
        log.debug("Loaded {} transactions in window", transactions.size());
        return aggregate(transactions);
    }
    
    /**
     * Returns rewards summary for specific customer for the given date range.
     * If the caller doesn't pass any dates, then last three months rewards will be shared by default.
     */
    @Transactional(readOnly = true)
    public CustomerRewardsResponse getRewardsForCustomer(Long customerId, LocalDate startDate, LocalDate endDate) {
        if (!customerRepository.existsById(customerId)) {
            log.warn("Customer not found: id={}", customerId);
            throw new CustomerNotFoundException(customerId);
        }
        DateRange dateRange = resolveRange(startDate, endDate);
        log.debug("Fetching rewards for customer={} between {} and {}",
                customerId, dateRange.start(), dateRange.end());
        List<Transaction> transactions =
                transactionRepository.findByCustomerInRange(customerId, dateRange.start(), dateRange.end());
        if (transactions.isEmpty()) {
            log.info("No transactions found for customer={} in window", customerId);
            String customerName = customerRepository.findById(customerId)
                    .map(customer -> customer.getName())
                    .orElse("");
            return new CustomerRewardsResponse(customerId, customerName, new LinkedHashMap<>(), 0L);
        }
        return aggregate(transactions).get(0);
    }

    private List<CustomerRewardsResponse> aggregate(List<Transaction> transactions) {
        Map<Long, List<Transaction>> transactionsByCustomerId = new TreeMap<>();
        for (Transaction transaction : transactions) {
            Long customerId = transaction.getCustomer().getId();
            transactionsByCustomerId
                    .computeIfAbsent(customerId, id -> new ArrayList<>())
                    .add(transaction);
        }

        List<CustomerRewardsResponse> rewardSummaries = new ArrayList<>(transactionsByCustomerId.size());
        for (List<Transaction> customerTransactions : transactionsByCustomerId.values()) {
            rewardSummaries.add(buildResponse(customerTransactions));
        }
        return rewardSummaries;
    }

    private CustomerRewardsResponse buildResponse(List<Transaction> customerTransactions) {
        Long customerId = customerTransactions.get(0).getCustomer().getId();
        String customerName = customerTransactions.get(0).getCustomer().getName();

        Map<String, Long> pointsByMonth = new TreeMap<>();
        long totalPoints = 0L;
        for (Transaction transaction : customerTransactions) {
            long earnedPoints = RewardsCalculator.pointsFor(transaction.getAmount());
            String monthKey = YearMonth.from(transaction.getTransactionDate()).format(MONTH_FORMATTER);
            pointsByMonth.merge(monthKey, earnedPoints, Long::sum);
            totalPoints += earnedPoints;
        }
        return new CustomerRewardsResponse(customerId, customerName, pointsByMonth, totalPoints);
    }

    // Date Handling 
    private DateRange resolveRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null ^ endDate == null) {
            throw new IllegalArgumentException(
                    "Both 'start' and 'end' must be provided together, or omit both to use the default last 3 months.");
        }

        if (startDate != null) {
            validateOrder(startDate, endDate);
            return new DateRange(startDate, endDate);
        }

        LocalDate today = LocalDate.now();
        LocalDate defaultStartDate = today.minusMonths(DEFAULT_WINDOW_MONTHS).withDayOfMonth(1);
        return new DateRange(defaultStartDate, today);
    }

    private static void validateOrder(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("'end' must not be before 'start'");
        }
    }
}
