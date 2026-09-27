package com.kaushiksridhar.finledger.split;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupExpenseRepository extends JpaRepository<GroupExpense, Long> {

    // Loads the payer and every share (with its member) in one query instead of one per expense
    @EntityGraph(attributePaths = { "paidBy", "shares", "shares.member" })
    List<GroupExpense> findByGroupIdOrderByExpenseDateDescIdDesc(Long groupId);

    @EntityGraph(attributePaths = { "paidBy", "shares", "shares.member" })
    Optional<GroupExpense> findByIdAndGroupId(Long id, Long groupId);

    /** How much each member has paid for, across the user's groups (or one group if groupId is given). */
    @Query("""
            select e.paidBy.id as memberId, sum(e.amountPaise) as total
            from GroupExpense e
            where e.group.user.id = :userId
              and (:groupId is null or e.group.id = :groupId)
            group by e.paidBy.id
            """)
    List<MemberTotal> paidByMember(@Param("userId") Long userId, @Param("groupId") Long groupId);

    /** How much each member owes for expenses, across the user's groups (or one group). */
    @Query("""
            select s.member.id as memberId, sum(s.sharePaise) as total
            from ExpenseShare s
            where s.expense.group.user.id = :userId
              and (:groupId is null or s.expense.group.id = :groupId)
            group by s.member.id
            """)
    List<MemberTotal> owedByMember(@Param("userId") Long userId, @Param("groupId") Long groupId);

    /** Total spent per group, for the groups list. */
    @Query("""
            select e.group.id as groupId, sum(e.amountPaise) as total
            from GroupExpense e
            where e.group.user.id = :userId
            group by e.group.id
            """)
    List<GroupTotal> totalsByGroup(@Param("userId") Long userId);

    @Query("select min(e.expenseDate) from GroupExpense e where e.group.id = :groupId")
    LocalDate firstExpenseDate(@Param("groupId") Long groupId);

    long countByPaidById(Long memberId);

    @Query("select count(s) from ExpenseShare s where s.member.id = :memberId")
    long countSharesOf(@Param("memberId") Long memberId);

    interface GroupTotal {
        Long getGroupId();

        Number getTotal();
    }
}
