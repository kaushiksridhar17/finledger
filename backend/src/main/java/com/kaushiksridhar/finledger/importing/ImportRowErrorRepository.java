package com.kaushiksridhar.finledger.importing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, Long> {

    List<ImportRowError> findByBatchIdOrderByLineNumberAsc(Long batchId);
}
