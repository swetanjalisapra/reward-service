package com.retailer.rewards.controller;

import com.retailer.rewards.dto.CustomerRewardsResponse;
import com.retailer.rewards.exception.CustomerNotFoundException;
import com.retailer.rewards.exception.GlobalExceptionHandler;
import com.retailer.rewards.service.RewardsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RewardsController.class)
@Import(GlobalExceptionHandler.class)
class RewardsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RewardsService rewardsService;

    @Test
    void getRewardsForAllCustomers_returnsOk() throws Exception {
        Map<String, Long> monthly = new LinkedHashMap<>();
        monthly.put("2026-01", 90L);
        when(rewardsService.getRewards(any(), any())).thenReturn(
                List.of(new CustomerRewardsResponse(1L, "Alice", monthly, 90L)));

        mockMvc.perform(get("/api/v1/rewards").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(1))
                .andExpect(jsonPath("$[0].customerName").value("Alice"))
                .andExpect(jsonPath("$[0].monthlyPoints['2026-01']").value(90))
                .andExpect(jsonPath("$[0].totalPoints").value(90));
    }

    @Test
    void getRewardsForAllCustomers_passesDateParameters() throws Exception {
        when(rewardsService.getRewards(eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 3, 31))))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/rewards")
                        .param("start", "2026-01-01")
                        .param("end", "2026-03-31"))
                .andExpect(status().isOk());
    }

    @Test
    void getRewardsForCustomer_returnsOk() throws Exception {
        when(rewardsService.getRewardsForCustomer(eq(1L), any(), any()))
                .thenReturn(new CustomerRewardsResponse(1L, "Alice", Map.of("2026-02", 30L), 30L));

        mockMvc.perform(get("/api/v1/rewards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.totalPoints").value(30));
    }

    @Test
    void getRewardsForCustomer_returns404WhenMissing() throws Exception {
        when(rewardsService.getRewardsForCustomer(eq(99L), any(), any()))
                .thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/api/v1/rewards/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Customer not found: 99"));
    }

    @Test
    void getRewardsForCustomer_returns400WhenServiceThrowsIllegalArgument() throws Exception {
        when(rewardsService.getRewardsForCustomer(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("'end' must not be before 'start'"));

        mockMvc.perform(get("/api/v1/rewards/1")
                        .param("start", "2026-05-01")
                        .param("end", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("'end' must not be before 'start'"));
    }

    @Test
    void getRewardsForCustomer_returns400WhenDateMalformed() throws Exception {
        mockMvc.perform(get("/api/v1/rewards/1").param("start", "not-a-date").param("end", "2026-03-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("YYYY-MM-DD")));
    }

    @Test
    void getRewardsForCustomer_returns400WhenCustomerIdNotANumber() throws Exception {
        mockMvc.perform(get("/api/v1/rewards/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
