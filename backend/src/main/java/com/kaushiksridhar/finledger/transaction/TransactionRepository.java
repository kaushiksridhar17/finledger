package com.kaushiksridhar.finledger.transaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kaushiksridhar.finledger.category.CategoryKind;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Every filter is optional: passing null for a parameter switches that filter off.
     * "left join" keeps uncategorised transactions in the results.
     */
    String SEARCH_WHERE = """
            where t.user.id = :userId
              and (:accountId is null or t.account.id = :accountId)
              and (:categoryId is null or c.id = :categoryId)
              and (:fromDate is null or t.txnDate >= :fromDate)
              and (:toDate is null or t.txnDate <= :toDate)
              and (:direction is null
                   or (:direction = 'IN' and t.amountPaise > 0)
                   or (:direction = 'OUT' and t.amountPaise < 0))
              and (:search is null
                   or lower(t.description) like :search
                   or lower(t.merchant) like :search)
            """;

    // @EntityGraph loads each row's account and category in the same query, avoiding one extra query per row
    @EntityGraph(attributePaths = { "account", "category" })
    @Query(value = "select t from Transaction t left join t.category c " + SEARCH_WHERE,
            countQuery = "select count(t) from Transaction t left join t.category c " + SEARCH_WHERE)
    Page<Transaction> search(
            @Param("userId") Long userId,
            @Param("accountId") Long accountId,
            @Param("categoryId") Long categoryId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("direction") String direction,
            @Param("search") String search,
            Pageable pageable);

    @EntityGraph(attributePaths = { "account", "category" })
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    boolean existsByAccountId(Long accountId);

    /** Sum of all transactions per account, for balances. */
    @Query("""
            select t.account.id as accountId, sum(t.amountPaise) as total
            from Transaction t
            where t.user.id = :userId
            group by t.account.id
            """)
    List<AccountTotal> sumByAccount(@Param("userId") Long userId);

    default Map<Long, Long> totalsByAccount(Long userId) {
        return sumByAccount(userId).stream()
                .collect(Collectors.toMap(AccountTotal::getAccountId, AccountTotal::getTotal));
    }

    interface AccountTotal {
        Long getAccountId();

        Long getTotal();
    }

    // ------------------------------------------------------------------ dashboard

    /**
     * Income and spending per calendar month. Transfers (moving money between your own accounts,
     * SIPs, credit card bills) are left out: they are neither earning nor spending.
     * Pass CategoryKind.TRANSFER as the "transfer" parameter.
     */
    @Query("""
            select year(t.txnDate) as yr,
                   month(t.txnDate) as mon,
                   sum(case when t.amountPaise > 0 then t.amountPaise else 0L end) as income,
                   sum(case when t.amountPaise < 0 then -t.amountPaise else 0L end) as spending
            from Transaction t left join t.category c
            where t.user.id = :userId
              and t.txnDate between :fromDate and :toDate
              and (c is null or c.kind <> :transfer)
            group by year(t.txnDate), month(t.txnDate)
            """)
    List<MonthlyTotalRow> monthlyTotals(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("transfer") CategoryKind transfer);

    /** Spending per category between two dates, biggest first. Uncategorised spending comes back with a null id. */
    @Query("""
            select c.id as categoryId,
                   c.name as name,
                   c.color as color,
                   sum(-t.amountPaise) as total
            from Transaction t left join t.category c
            where t.user.id = :userId
              and t.amountPaise < 0
              and t.txnDate between :fromDate and :toDate
              and (c is null or c.kind <> :transfer)
            group by c.id, c.name, c.color
            order by sum(-t.amountPaise) desc
            """)
    List<CategoryTotalRow> spendingByCategory(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("transfer") CategoryKind transfer);

    interface MonthlyTotalRow {
        Number getYr();

        Number getMon();

        Number getIncome();

        Number getSpending();
    }

    interface CategoryTotalRow {
        Long getCategoryId();

        String getName();

        String getColor();

        Number getTotal();
    }
}
