package com.kaushiksridhar.finledger.rules;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    // Oldest first: that's the order rules are tried in
    @EntityGraph(attributePaths = "category")
    List<CategoryRule> findByUserIdOrderByIdAsc(Long userId);

    Optional<CategoryRule> findByIdAndUserId(Long id, Long userId);
}
