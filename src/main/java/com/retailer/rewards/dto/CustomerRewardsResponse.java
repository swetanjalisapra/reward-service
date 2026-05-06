package com.retailer.rewards.dto;

import java.util.Map;

// Rewards Response Structure
public record CustomerRewardsResponse(
        Long customerId,
        String customerName,
        Map<String, Long> monthlyPoints,
        long totalPoints
) {
}
