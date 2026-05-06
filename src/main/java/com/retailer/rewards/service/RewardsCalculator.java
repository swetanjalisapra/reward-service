package com.retailer.rewards.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

//Rewards calculation
public final class RewardsCalculator {

    private static final BigDecimal LOWER_THRESHOLD = BigDecimal.valueOf(50);
    private static final long LOWER_THRESHOLD_DOLLARS = 50L;
    private static final long UPPER_THRESHOLD_DOLLARS = 100L;
    private static final long FLAT_POINTS_FOR_LOWER_BAND = 50L;
    private static final long POINTS_PER_DOLLAR_OVER_UPPER = 2L;

    private RewardsCalculator() {
        // Pure utility class — no instances, please.
    }

    public static long pointsFor(BigDecimal transactionAmount) {
        if (transactionAmount == null || transactionAmount.compareTo(LOWER_THRESHOLD) <= 0) {
            return 0L;
        }

        // Drop the cents — only whole dollars earn points.
        long wholeDollars = transactionAmount.setScale(0, RoundingMode.FLOOR).longValueExact();

        if (wholeDollars <= LOWER_THRESHOLD_DOLLARS) {
            return 0L;
        }
        if (wholeDollars <= UPPER_THRESHOLD_DOLLARS) {
            // Just the $50–$100 band: 1 point per dollar above $50.
            return wholeDollars - LOWER_THRESHOLD_DOLLARS;
        }
        // Above $100: a flat 50 points for the $50–$100 band, plus 2 points
        // for every dollar above $100.
        return FLAT_POINTS_FOR_LOWER_BAND
                + POINTS_PER_DOLLAR_OVER_UPPER * (wholeDollars - UPPER_THRESHOLD_DOLLARS);
    }
}
