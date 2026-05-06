package com.retailer.rewards.service;

import com.retailer.rewards.dto.CustomerRewardsResponse;
import com.retailer.rewards.exception.CustomerNotFoundException;
import com.retailer.rewards.repository.CustomerMonthlyPoints;
import com.retailer.rewards.repository.CustomerRepository;
import com.retailer.rewards.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

//Service Layer- Response formation and validations 
@Service
@RequiredArgsConstructor
@Slf4j
public class RewardsService {

    private static final int DEFAULT_WINDOW_MONTHS = 3;

    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerRewardsResponse> getRewards(LocalDate startDate, LocalDate endDate) {
        DateRange range = resolveRange(startDate, endDate);
        log.debug("Aggregating rewards for all customers between {} and {}", range.start(), range.end());

        List<CustomerMonthlyPoints> rows =
                transactionRepository.aggregatePointsByCustomerAndMonth(range.start(), range.end());
        log.debug("DB returned {} aggregated (customer, month) rows", rows.size());

        return generateResponses(rows);
    }

    @Transactional(readOnly = true)
    public CustomerRewardsResponse getRewardsForCustomer(Long customerId,
                                                         LocalDate startDate,
                                                         LocalDate endDate) {
        // Validation for customer ID
        String customerName = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId))
                .getName();

        DateRange range = resolveRange(startDate, endDate);
        log.debug("Aggregating rewards for customer={} between {} and {}",
                customerId, range.start(), range.end());

        List<CustomerMonthlyPoints> rows =
                transactionRepository.aggregatePointsForCustomer(customerId, range.start(), range.end());

        if (rows.isEmpty()) {
            // No Transactions available, send empty response.
            return new CustomerRewardsResponse(customerId, customerName, new LinkedHashMap<>(), 0L);
        }
        // Transactions found for given CUstomer, return Response.
        return generateResponses(rows).get(0);
    }

    /*
     * Turns the flat (customerId, year, month, points) rows the DB gave us
     * into one CustomerRewardsResponse per customer.
     */
    private List<CustomerRewardsResponse> generateResponses(List<CustomerMonthlyPoints> rows) {
        // TreeMap keeps customers in id order without an extra sort step.
        Map<Long, CustomerAccumulator> byCustomer = new TreeMap<>();

        for (CustomerMonthlyPoints row : rows) {
            CustomerAccumulator acc = byCustomer.computeIfAbsent(
                    row.getCustomerId(),
                    id -> new CustomerAccumulator(id, row.getCustomerName()));
            acc.add(formatMonth(row.getYear(), row.getMonth()), row.getPoints());
        }

        List<CustomerRewardsResponse> responses = new ArrayList<>(byCustomer.size());
        for (CustomerAccumulator acc : byCustomer.values()) {
            responses.add(acc.toResponse());
        }
        return responses;
    }

    // Formats (2026, 5) as "2026-05"
    private static String formatMonth(Integer year, Integer month) {
        return String.format("%04d-%02d", year, month);
    }

    // Tiny holder that collects monthly buckets and the running total for one customer.
    private static final class CustomerAccumulator {
        private final Long id;
        private final String name;
        private final Map<String, Long> monthlyPoints = new TreeMap<>();
        private long total;

        CustomerAccumulator(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        void add(String month, Long points) {
            long value = points == null ? 0L : points;
            monthlyPoints.merge(month, value, Long::sum);
            total += value;
        }

        CustomerRewardsResponse toResponse() {
            return new CustomerRewardsResponse(id, name, monthlyPoints, total);
        }
    }

    // Date Range Validations
    private DateRange resolveRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null ^ endDate == null) {
            throw new IllegalArgumentException(
                    "Both 'start' and 'end' must be provided together, or omit both to use the default last 3 months.");
        }
        if (startDate != null) {
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("'end' must not be before 'start'");
            }
            return new DateRange(startDate, endDate);
        }
        LocalDate today = LocalDate.now();
        return new DateRange(today.minusMonths(DEFAULT_WINDOW_MONTHS).withDayOfMonth(1), today);
    }
}
