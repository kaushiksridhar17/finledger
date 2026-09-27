package com.kaushiksridhar.finledger.investment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kaushiksridhar.finledger.transaction.Transaction;

public interface MfTransactionRepository extends JpaRepository<MfTransaction, Long> {

    List<MfTransaction> findByUserIdOrderByTxnDateAscIdAsc(Long userId);

    List<MfTransaction> findByUserIdAndSchemeCode(Long userId, Integer schemeCode);

    Optional<MfTransaction> findByIdAndUserId(Long id, Long userId);

    /** Purchases that came from linked SIPs, with their link. */
    @Query("select m from MfTransaction m join fetch m.sipLink where m.user.id = :userId")
    List<MfTransaction> findFromSips(@Param("userId") Long userId);

    /** Money that left the user's accounts on or after a date, with its category, oldest first. */
    @Query("""
            select t from Transaction t
            left join fetch t.category
            where t.user.id = :userId
              and t.amountPaise < 0
              and t.txnDate >= :fromDate
            order by t.txnDate, t.id
            """)
    List<Transaction> outgoingSince(@Param("userId") Long userId, @Param("fromDate") LocalDate fromDate);
}
