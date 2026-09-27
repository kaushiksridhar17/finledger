package com.kaushiksridhar.finledger.split;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SplitGroupRepository extends JpaRepository<SplitGroup, Long> {

    List<SplitGroup> findByUserIdOrderByIdDesc(Long userId);

    Optional<SplitGroup> findByIdAndUserId(Long id, Long userId);
}
