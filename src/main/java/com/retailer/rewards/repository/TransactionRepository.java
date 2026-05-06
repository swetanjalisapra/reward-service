package com.retailer.rewards.repository;

import com.retailer.rewards.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

	 // Aggregated data for all customers based on Date Range
    @Query("""
            SELECT c.id AS customerId,
                    c.name AS customerName,
                    EXTRACT(YEAR  FROM t.transactionDate) AS year,
                    EXTRACT(MONTH FROM t.transactionDate) AS month,
                    SUM(
                        CASE
                            WHEN t.amount > 100 THEN 50 + 2 * (FLOOR(t.amount) - 100)
                            WHEN t.amount >  50 THEN FLOOR(t.amount) -  50
                            ELSE 0
                        END
                    ) AS points
            FROM Transaction t JOIN t.customer c
            WHERE t.transactionDate BETWEEN :start AND :end
            GROUP BY c.id, c.name,
                     EXTRACT(YEAR  FROM t.transactionDate),
                     EXTRACT(MONTH FROM t.transactionDate)
            ORDER BY c.id,
                     EXTRACT(YEAR  FROM t.transactionDate),
                     EXTRACT(MONTH FROM t.transactionDate)
            """)
    List<CustomerMonthlyPoints> aggregatePointsByCustomerAndMonth(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    // Customer specific aggregated data based on customer ID and Date Range
    @Query("""
            SELECT c.id AS customerId,
                    c.name AS customerName,
                    EXTRACT(YEAR  FROM t.transactionDate) AS year,
                    EXTRACT(MONTH FROM t.transactionDate) AS month,
                    SUM(
                        CASE
                            WHEN t.amount > 100 THEN 50 + 2 * (FLOOR(t.amount) - 100)
                            WHEN t.amount >  50 THEN FLOOR(t.amount) -  50
                            ELSE 0
                        END
                    ) AS points
            FROM Transaction t
            JOIN t.customer c
            WHERE c.id = :customerId
              AND t.transactionDate BETWEEN :start AND :end
            GROUP BY c.id, c.name,
                     EXTRACT(YEAR  FROM t.transactionDate),
                     EXTRACT(MONTH FROM t.transactionDate)
            ORDER BY EXTRACT(YEAR  FROM t.transactionDate),
                     EXTRACT(MONTH FROM t.transactionDate)
            """)
    List<CustomerMonthlyPoints> aggregatePointsForCustomer(
            @Param("customerId") Long customerId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
