package com.retailer.rewards.repository;

//aggregated rewards based on customer in given month
public interface CustomerMonthlyPoints {

    Long getCustomerId();

    String getCustomerName();

    
    Integer getYear();
    Integer getMonth();

    // Sum of reward points earned in that (customer, month) bucket.
    Long getPoints();
}
