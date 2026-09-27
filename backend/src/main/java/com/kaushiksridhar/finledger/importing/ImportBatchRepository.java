package com.kaushiksridhar.finledger.importing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    @EntityGraph(attributePaths = "account")
    List<ImportBatch> findTop20ByUserIdOrderByIdDesc(Long userId);

    @EntityGraph(attributePaths = "account")
    Optional<ImportBatch> findByIdAndUserId(Long id, Long userId);

    List<ImportBatch> findByStatusIn(Collection<ImportStatus> statuses);
}
