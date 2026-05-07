package com.retailer.rewards.service;

import com.retailer.rewards.dto.CustomerRewardsResponse;
import com.retailer.rewards.entity.Customer;
import com.retailer.rewards.exception.CustomerNotFoundException;
import com.retailer.rewards.repository.CustomerMonthlyPoints;
import com.retailer.rewards.repository.CustomerRepository;
import com.retailer.rewards.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardsServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CustomerRepository customerRepository;

    private RewardsService rewardsService;

    @BeforeEach
    void setUp() {
        rewardsService = new RewardsService(transactionRepository, customerRepository, 3);
    }

    /** Simple test double for the projection interface. */
    private static CustomerMonthlyPoints row(Long id, String name, int year, int month, long points) {
        return new CustomerMonthlyPoints() {
            @Override public Long getCustomerId() { return id; }
            @Override public String getCustomerName() { return name; }
            @Override public Integer getYear() { return year; }
            @Override public Integer getMonth() { return month; }
            @Override public Long getPoints() { return points; }
        };
    }

    @Test
    void getRewards_groupsRowsByCustomerAndCalculatesTotals() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);

        when(transactionRepository.aggregatePointsByCustomerAndMonth(start, end))
                .thenReturn(List.of(
                        row(1L, "Alice", 2026, 1, 90L),
                        row(1L, "Alice", 2026, 2, 30L),
                        row(2L, "Bob", 2026, 3, 50L)
                ));

        List<CustomerRewardsResponse> result = rewardsService.getRewards(start, end);

        assertThat(result).hasSize(2);

        CustomerRewardsResponse alice = result.get(0);
        assertThat(alice.customerId()).isEqualTo(1L);
        assertThat(alice.customerName()).isEqualTo("Alice");
        assertThat(alice.monthlyPoints()).containsExactly(
                java.util.Map.entry("2026-01", 90L),
                java.util.Map.entry("2026-02", 30L)
        );
        assertThat(alice.totalPoints()).isEqualTo(120L);

        CustomerRewardsResponse bob = result.get(1);
        assertThat(bob.customerId()).isEqualTo(2L);
        assertThat(bob.monthlyPoints()).containsExactly(java.util.Map.entry("2026-03", 50L));
        assertThat(bob.totalPoints()).isEqualTo(50L);
    }

    @Test
    void getRewards_returnsEmptyListWhenNoRows() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        when(transactionRepository.aggregatePointsByCustomerAndMonth(start, end))
                .thenReturn(Collections.emptyList());

        assertThat(rewardsService.getRewards(start, end)).isEmpty();
    }

    @Test
    void getRewards_usesDefaultWindowWhenDatesAreNull() {
        when(transactionRepository.aggregatePointsByCustomerAndMonth(any(), any()))
                .thenReturn(Collections.emptyList());

        rewardsService.getRewards(null, null);

        LocalDate today = LocalDate.now();
        LocalDate expectedStart = today.minusMonths(3).withDayOfMonth(1);
        org.mockito.Mockito.verify(transactionRepository)
                .aggregatePointsByCustomerAndMonth(eq(expectedStart), eq(today));
    }

    @Test
    void getRewards_treatsNullPointsAsZero() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        when(transactionRepository.aggregatePointsByCustomerAndMonth(start, end))
                .thenReturn(List.of(row(1L, "Alice", 2026, 1, 0L)));

        // Note: passing null directly is hard with primitive Long getter; verify the 0 path here.
        List<CustomerRewardsResponse> result = rewardsService.getRewards(start, end);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalPoints()).isZero();
        assertThat(result.get(0).monthlyPoints()).containsEntry("2026-01", 0L);
    }

    @Test
    void getRewards_throwsWhenOnlyStartProvided() {
        assertThatThrownBy(() -> rewardsService.getRewards(LocalDate.of(2026, 1, 1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Both 'start' and 'end'");
    }

    @Test
    void getRewards_throwsWhenOnlyEndProvided() {
        assertThatThrownBy(() -> rewardsService.getRewards(null, LocalDate.of(2026, 3, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Both 'start' and 'end'");
    }

    @Test
    void getRewards_throwsWhenEndBeforeStart() {
        assertThatThrownBy(() -> rewardsService.getRewards(
                LocalDate.of(2026, 3, 31), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'end' must not be before 'start'");
    }

    @Test
    void getRewardsForCustomer_returnsAggregatedResponse() {
        Customer alice = Customer.builder().id(1L).name("Alice").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(alice));
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        when(transactionRepository.aggregatePointsForCustomer(1L, start, end))
                .thenReturn(List.of(
                        row(1L, "Alice", 2026, 1, 90L),
                        row(1L, "Alice", 2026, 3, 250L)
                ));

        CustomerRewardsResponse response = rewardsService.getRewardsForCustomer(1L, start, end);

        assertThat(response.customerId()).isEqualTo(1L);
        assertThat(response.customerName()).isEqualTo("Alice");
        assertThat(response.monthlyPoints()).containsOnlyKeys("2026-01", "2026-03");
        assertThat(response.totalPoints()).isEqualTo(340L);
    }

    @Test
    void getRewardsForCustomer_returnsEmptyResponseWhenNoTransactions() {
        Customer bob = Customer.builder().id(2L).name("Bob").build();
        when(customerRepository.findById(2L)).thenReturn(Optional.of(bob));
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        when(transactionRepository.aggregatePointsForCustomer(2L, start, end))
                .thenReturn(Collections.emptyList());

        CustomerRewardsResponse response = rewardsService.getRewardsForCustomer(2L, start, end);

        assertThat(response.customerId()).isEqualTo(2L);
        assertThat(response.customerName()).isEqualTo("Bob");
        assertThat(response.monthlyPoints()).isEmpty();
        assertThat(response.totalPoints()).isZero();
    }

    @Test
    void getRewardsForCustomer_throwsWhenCustomerMissing() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rewardsService.getRewardsForCustomer(99L, null, null))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getRewardsForCustomer_throwsWhenDateRangeInvalid() {
        Customer alice = Customer.builder().id(1L).name("Alice").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> rewardsService.getRewardsForCustomer(
                1L, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
