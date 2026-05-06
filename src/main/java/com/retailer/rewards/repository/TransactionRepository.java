package com.retailer.rewards.repository;

import com.retailer.rewards.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.customer
            WHERE t.transactionDate BETWEEN :start AND :end
            """)
    List<Transaction> findInRange(@Param("start") LocalDate start,
                                  @Param("end") LocalDate end);

    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.customer c
            WHERE c.id = :customerId
              AND t.transactionDate BETWEEN :start AND :end
            """)
    List<Transaction> findByCustomerInRange(@Param("customerId") Long customerId,
                                            @Param("start") LocalDate start,
                                            @Param("end") LocalDate end);
}
