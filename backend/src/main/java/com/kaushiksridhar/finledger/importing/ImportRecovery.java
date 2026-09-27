package com.kaushiksridhar.finledger.importing;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uploaded files are held in memory while they wait for the import thread, so a restart loses them.
 * On startup, any import still marked QUEUED or PROCESSING is marked FAILED with a clear message,
 * instead of looking stuck forever.
 */
@Component
public class ImportRecovery {

    private static final Logger log = LoggerFactory.getLogger(ImportRecovery.class);

    private final ImportBatchRepository batchRepository;
    private final Clock clock;

    public ImportRecovery(ImportBatchRepository batchRepository, Clock clock) {
        this.batchRepository = batchRepository;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedImports() {
        List<ImportBatch> interrupted = batchRepository.findByStatusIn(List.of(ImportStatus.QUEUED, ImportStatus.PROCESSING));
        for (ImportBatch batch : interrupted) {
            batch.setStatus(ImportStatus.FAILED);
            batch.setErrorMessage("The server restarted before this import finished. Please upload the file again.");
            batch.setCompletedAt(clock.instant());
        }
        if (!interrupted.isEmpty()) {
            log.warn("Marked {} interrupted import(s) as failed", interrupted.size());
        }
    }
}
