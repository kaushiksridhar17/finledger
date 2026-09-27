package com.kaushiksridhar.finledger.recurring;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kaushiksridhar.finledger.transaction.Transaction;

public interface RecurringPaymentRepository extends JpaRepository<RecurringPayment, Long> {

    @EntityGraph(attributePaths = { "category", "account" })
    List<RecurringPayment> findByUserIdOrderByNextDueOnAscNameAsc(Long userId);

    List<RecurringPayment> findByUserId(Long userId);

    @EntityGraph(attributePaths = { "category", "account" })
    Optional<RecurringPayment> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = { "category", "account" })
    List<RecurringPayment> findByUserIdAndStatusAndDirectionAndNextDueOnBetweenOrderByNextDueOnAsc(
            Long userId, RecurringStatus status, Direction direction, LocalDate from, LocalDate to);

    /** Everything the detector needs, with each transaction's category loaded in the same query. */
    @Query("""
            select t from Transaction t left join fetch t.category
            where t.user.id = :userId and t.txnDate >= :since
            """)
    List<Transaction> transactionsSince(@Param("userId") Long userId, @Param("since") LocalDate since);
}
