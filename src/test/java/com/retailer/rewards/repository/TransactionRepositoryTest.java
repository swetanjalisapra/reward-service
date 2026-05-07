package com.retailer.rewards.repository;

import com.retailer.rewards.entity.Customer;
import com.retailer.rewards.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
class TransactionRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Customer alice;
    private Customer bob;

    @BeforeEach
    void setUp() {
        alice = customerRepository.save(Customer.builder().name("Alice").build());
        bob = customerRepository.save(Customer.builder().name("Bob").build());

        // Alice: $120 in Jan 2026 -> 90 pts; $75 in Feb 2026 -> 25 pts; $40 ignored
        save(alice, "120.00", LocalDate.of(2026, 1, 10));
        save(alice, "75.00", LocalDate.of(2026, 2, 5));
        save(alice, "40.00", LocalDate.of(2026, 2, 15));

        // Bob: $200 in Mar 2026 -> 250 pts
        save(bob, "200.00", LocalDate.of(2026, 3, 20));

        // Out-of-range transaction (should never appear in results)
        save(alice, "500.00", LocalDate.of(2025, 12, 1));
    }

    private void save(Customer customer, String amount, LocalDate date) {
        transactionRepository.save(Transaction.builder()
                .customer(customer)
                .amount(new BigDecimal(amount))
                .transactionDate(date)
                .build());
    }

    @Test
    void aggregatePointsByCustomerAndMonth_groupsAllCustomersWithinRange() {
        List<CustomerMonthlyPoints> rows = transactionRepository.aggregatePointsByCustomerAndMonth(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(rows).hasSize(3);

        CustomerMonthlyPoints aliceJan = rows.get(0);
        assertThat(aliceJan.getCustomerId()).isEqualTo(alice.getId());
        assertThat(aliceJan.getYear()).isEqualTo(2026);
        assertThat(aliceJan.getMonth()).isEqualTo(1);
        assertThat(aliceJan.getPoints()).isEqualTo(90L);

        CustomerMonthlyPoints aliceFeb = rows.get(1);
        assertThat(aliceFeb.getCustomerId()).isEqualTo(alice.getId());
        assertThat(aliceFeb.getMonth()).isEqualTo(2);
        assertThat(aliceFeb.getPoints()).isEqualTo(25L);

        CustomerMonthlyPoints bobMar = rows.get(2);
        assertThat(bobMar.getCustomerId()).isEqualTo(bob.getId());
        assertThat(bobMar.getMonth()).isEqualTo(3);
        assertThat(bobMar.getPoints()).isEqualTo(250L);
    }

    @Test
    void aggregatePointsByCustomerAndMonth_excludesOutOfRange() {
        List<CustomerMonthlyPoints> rows = transactionRepository.aggregatePointsByCustomerAndMonth(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));

        assertThat(rows).isEmpty();
    }

    @Test
    void aggregatePointsForCustomer_returnsOnlyThatCustomer() {
        List<CustomerMonthlyPoints> rows = transactionRepository.aggregatePointsForCustomer(
                alice.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(r -> r.getCustomerId().equals(alice.getId()));
        assertThat(rows.get(0).getPoints()).isEqualTo(90L);
        assertThat(rows.get(1).getPoints()).isEqualTo(25L);
    }

    @Test
    void aggregatePointsForCustomer_returnsEmptyWhenNoTransactions() {
        List<CustomerMonthlyPoints> rows = transactionRepository.aggregatePointsForCustomer(
                bob.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(rows).isEmpty();
    }
}
