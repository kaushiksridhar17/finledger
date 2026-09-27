package com.kaushiksridhar.finledger.budget;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    @EntityGraph(attributePaths = "category")
    List<Budget> findByUserIdOrderByIdAsc(Long userId);

    @EntityGraph(attributePaths = "category")
    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndCategoryId(Long userId, Long categoryId);

    /**
     * The sum of every transaction in each category between two dates. Spending is negative,
     * so a refund put in the same category (positive) reduces what counts as spent.
     */
    @Query("""
            select t.category.id as categoryId, sum(t.amountPaise) as total
            from Transaction t
            where t.user.id = :userId
              and t.category.id in :categoryIds
              and t.txnDate between :fromDate and :toDate
            group by t.category.id
            """)
    List<CategorySum> netByCategory(
            @Param("userId") Long userId,
            @Param("categoryIds") Collection<Long> categoryIds,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    interface CategorySum {
        Long getCategoryId();

        Number getTotal();
    }
}
