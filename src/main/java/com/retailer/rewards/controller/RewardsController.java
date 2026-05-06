package com.retailer.rewards.controller;

import com.retailer.rewards.dto.CustomerRewardsResponse;
import com.retailer.rewards.service.RewardsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rewards")
@RequiredArgsConstructor
@Slf4j
public class RewardsController {

    private final RewardsService rewardsService;

    /*Lists Reward Points for Every Customer for given range.
     *  If no range provided then data is shared for last three months by default
     */
   
    @GetMapping
    public ResponseEntity<List<CustomerRewardsResponse>> getRewardsForAllCustomers(
            @RequestParam(name = "start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("GET /api/v1/rewards start={} end={}", startDate, endDate);
        List<CustomerRewardsResponse> rewards = rewardsService.getRewards(startDate, endDate);
        log.debug("Returning rewards for {} customers", rewards.size());
        return ResponseEntity.ok(rewards);
    }
    
    /*Lists Reward Points for specific Customer with given ID for given range.
     *  If no range provided then data is shared for last three months by default for specific customer.
     */

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerRewardsResponse> getRewardsForCustomer(
            @PathVariable Long customerId,
            @RequestParam(name = "start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("GET /api/v1/rewards/{} start={} end={}", customerId, startDate, endDate);
        return ResponseEntity.ok(rewardsService.getRewardsForCustomer(customerId, startDate, endDate));
    }
}
