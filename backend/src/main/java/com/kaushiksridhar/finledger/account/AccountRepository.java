package com.kaushiksridhar.finledger.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdOrderByNameAsc(Long userId);

    // Always look up by id AND owner, so one user can never reach another user's account
    Optional<Account> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}
