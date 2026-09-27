package com.kaushiksridhar.finledger.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdOrderByNameAsc(Long userId);

    // Always look up by id AND owner, so one user can never reach another user's account
    Optional<Account> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    /** SELECT ... FOR UPDATE: holds a lock on the account row until the transaction ends. Used by imports. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
