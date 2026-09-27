package com.kaushiksridhar.finledger.user;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** How many demo users are still alive. */
    long countByDemoExpiresAtAfter(Instant now);

    /**
     * One SQL DELETE for every expired demo user. The database's ON DELETE CASCADE
     * foreign keys remove their sessions, accounts, categories and transactions too.
     */
    @Modifying
    @Query("delete from User u where u.demoExpiresAt < :now")
    int deleteExpiredDemoUsers(@Param("now") Instant now);
}
