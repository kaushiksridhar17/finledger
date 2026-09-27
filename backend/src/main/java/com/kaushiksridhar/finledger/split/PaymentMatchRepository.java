package com.kaushiksridhar.finledger.split;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kaushiksridhar.finledger.transaction.Transaction;

public interface PaymentMatchRepository extends JpaRepository<PaymentMatch, Long> {

    @Query("""
            select m from PaymentMatch m
            join fetch m.member
            join fetch m.transaction
            where m.group.user.id = :userId
            """)
    List<PaymentMatch> findAllForUser(@Param("userId") Long userId);

    @Query("""
            select m from PaymentMatch m
            join fetch m.member
            join fetch m.transaction
            where m.group.id = :groupId and m.status = :status
            order by m.id
            """)
    List<PaymentMatch> findByGroupAndStatus(@Param("groupId") Long groupId, @Param("status") MatchStatus status);

    @Query("""
            select m from PaymentMatch m
            join fetch m.member
            join fetch m.transaction
            where m.id = :id and m.group.id = :groupId
            """)
    Optional<PaymentMatch> findInGroup(@Param("id") Long id, @Param("groupId") Long groupId);

    Optional<PaymentMatch> findByMemberIdAndTransactionId(Long memberId, Long transactionId);

    /**
     * Money that came into the user's accounts since a date and isn't already recorded as a repayment.
     * These are the candidates for "a friend may have paid you back".
     */
    @Query("""
            select t from Transaction t
            where t.user.id = :userId
              and t.amountPaise > 0
              and t.txnDate >= :fromDate
              and not exists (select s.id from Settlement s where s.transaction = t)
            order by t.txnDate, t.id
            """)
    List<Transaction> unlinkedIncomingSince(@Param("userId") Long userId, @Param("fromDate") LocalDate fromDate);
}
