package com.retailer.rewards.service;

import java.time.LocalDate;

//Inclusive date window used by the rewards queries.
record DateRange(LocalDate start, LocalDate end) {
}
