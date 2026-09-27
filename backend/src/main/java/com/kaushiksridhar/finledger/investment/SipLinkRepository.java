package com.kaushiksridhar.finledger.investment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SipLinkRepository extends JpaRepository<SipLink, Long> {

    List<SipLink> findByUserIdOrderByIdAsc(Long userId);

    Optional<SipLink> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndMatchKey(Long userId, String matchKey);

    @Query("select distinct l.user.id from SipLink l")
    List<Long> userIdsWithLinks();
}
