package com.kaushiksridhar.finledger.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /**
     * Loads a session with its user and locks the row (SELECT ... FOR UPDATE) until the transaction ends.
     * Two refreshes of the same session therefore run one after the other, never interleaved.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSession s join fetch s.user where s.id = :id")
    Optional<UserSession> findByIdForUpdate(@Param("id") Long id);
}
