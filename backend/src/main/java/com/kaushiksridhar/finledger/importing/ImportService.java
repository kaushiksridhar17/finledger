package com.kaushiksridhar.finledger.importing;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.kaushiksridhar.finledger.account.Account;
import com.kaushiksridhar.finledger.account.AccountService;
import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.importing.ImportBatchResponse.RowErrorResponse;
import com.kaushiksridhar.finledger.user.UserRepository;

/** The request side of an import: check the upload, record the batch, and report progress. */
@Service
public class ImportService {

    static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    private final ImportBatchRepository batchRepository;
    private final ImportRowErrorRepository rowErrorRepository;
    private final AccountService accountService;
    private final UserRepository userRepository;
    private final Clock clock;

    public ImportService(ImportBatchRepository batchRepository,
            ImportRowErrorRepository rowErrorRepository,
            AccountService accountService,
            UserRepository userRepository,
            Clock clock) {
        this.batchRepository = batchRepository;
        this.rowErrorRepository = rowErrorRepository;
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Checks the uploaded file and returns its contents. */
    public byte[] readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a CSV file to upload");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "The file is larger than 2 MB");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Upload a .csv file. Most banks offer CSV when you download a statement.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Couldn't read the uploaded file");
        }
    }

    @Transactional
    public ImportBatchResponse createBatch(long userId, long accountId, String originalFileName) {
        Account account = accountService.getOwned(userId, accountId);

        ImportBatch batch = new ImportBatch();
        batch.setUser(userRepository.getReferenceById(userId));
        batch.setAccount(account);
        batch.setFileName(cleanFileName(originalFileName));
        batch.setStatus(ImportStatus.QUEUED);
        batch.setCreatedAt(clock.instant());
        batchRepository.save(batch);

        return ImportBatchResponse.from(batch, List.of());
    }

    @Transactional(readOnly = true)
    public List<ImportBatchResponse> list(long userId) {
        return batchRepository.findTop20ByUserIdOrderByIdDesc(userId).stream()
                .map(batch -> ImportBatchResponse.from(batch, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ImportBatchResponse get(long userId, long batchId) {
        ImportBatch batch = batchRepository.findByIdAndUserId(batchId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Import not found"));

        List<RowErrorResponse> errors = rowErrorRepository.findByBatchIdOrderByLineNumberAsc(batchId).stream()
                .map(e -> new RowErrorResponse(e.getLineNumber(), e.getMessage()))
                .toList();

        return ImportBatchResponse.from(batch, errors);
    }

    @Transactional
    public void markFailed(long batchId, String message) {
        batchRepository.findById(batchId).ifPresent(batch -> {
            batch.setStatus(ImportStatus.FAILED);
            batch.setErrorMessage(message);
            batch.setCompletedAt(clock.instant());
        });
    }

    // Some browsers send "C:\fakepath\statement.csv"; keep just the file name
    private static String cleanFileName(String name) {
        String base = name == null ? "statement.csv" : name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).trim();
        if (base.isEmpty()) {
            base = "statement.csv";
        }
        return base.length() <= 255 ? base : base.substring(base.length() - 255);
    }
}
