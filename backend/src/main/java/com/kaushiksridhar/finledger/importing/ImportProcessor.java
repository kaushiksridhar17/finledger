package com.kaushiksridhar.finledger.importing;

import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.kaushiksridhar.finledger.account.Account;
import com.kaushiksridhar.finledger.account.AccountRepository;
import com.kaushiksridhar.finledger.importing.format.ParsedStatement;
import com.kaushiksridhar.finledger.importing.format.StatementFormatException;
import com.kaushiksridhar.finledger.importing.format.StatementParser;
import com.kaushiksridhar.finledger.importing.format.StatementRow;
import com.kaushiksridhar.finledger.rules.Categorizer;
import com.kaushiksridhar.finledger.rules.CategorizerFactory;

/**
 * The background side of an import. Runs on the import thread pool, after the upload request has
 * already returned, and moves the batch QUEUED -> PROCESSING -> COMPLETED (or FAILED).
 *
 * Saving happens in one database transaction: either every new row from the file is stored or none are.
 */
@Component
public class ImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImportProcessor.class);

    static final int MAX_STORED_ERRORS = 100;
    private static final int HASH_LOOKUP_CHUNK = 500;

    private static final String INSERT_TRANSACTION = """
            INSERT INTO transactions
                (user_id, account_id, category_id, import_batch_id, amount_paise, txn_date,
                 description, merchant, notes, dedupe_hash, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)
            """;

    private static final int[] INSERT_TYPES = {
            Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.BIGINT, Types.DATE,
            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP, Types.TIMESTAMP };

    private static final String EXISTING_HASHES = """
            SELECT dedupe_hash FROM transactions
            WHERE account_id = :accountId AND dedupe_hash IN (:hashes)
            """;

    private final ImportBatchRepository batchRepository;
    private final ImportRowErrorRepository rowErrorRepository;
    private final AccountRepository accountRepository;
    private final CategorizerFactory categorizerFactory;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ImportProcessor(ImportBatchRepository batchRepository,
            ImportRowErrorRepository rowErrorRepository,
            AccountRepository accountRepository,
            CategorizerFactory categorizerFactory,
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.batchRepository = batchRepository;
        this.rowErrorRepository = rowErrorRepository;
        this.accountRepository = accountRepository;
        this.categorizerFactory = categorizerFactory;
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Async(ImportConfig.EXECUTOR)
    public void process(long batchId, byte[] content) {
        try {
            transactionTemplate.executeWithoutResult(status -> markProcessing(batchId));

            ParsedStatement statement = StatementParser.parse(new String(content, StandardCharsets.UTF_8));

            transactionTemplate.executeWithoutResult(status -> save(batchId, statement));
        } catch (StatementFormatException e) {
            fail(batchId, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Import {} failed", batchId, e);
            fail(batchId, "Something went wrong while importing this file. Please try again.");
        }
    }

    private void markProcessing(long batchId) {
        batchRepository.findById(batchId).orElseThrow().setStatus(ImportStatus.PROCESSING);
    }

    private void save(long batchId, ParsedStatement statement) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow();
        long userId = batch.getUser().getId();

        // Lock the account (SELECT ... FOR UPDATE) so two imports into the same account run one after
        // the other. Otherwise both could check for duplicates before either had saved anything.
        Account account = accountRepository.findByIdForUpdate(batch.getAccount().getId()).orElseThrow();
        long accountId = account.getId();

        List<StatementRow> rows = statement.rows();
        List<String> hashes = DedupeHashes.compute(accountId, rows);
        Set<String> alreadyImported = existingHashes(accountId, hashes);
        Categorizer categorizer = categorizerFactory.forUser(userId);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        List<Object[]> inserts = new ArrayList<>();
        int duplicates = 0;

        for (int i = 0; i < rows.size(); i++) {
            String hash = hashes.get(i);
            if (alreadyImported.contains(hash)) {
                duplicates++;
                continue;
            }

            StatementRow row = rows.get(i);
            Categorizer.Result category = categorizer.categorize(row.description(), row.amountPaise());
            inserts.add(new Object[] {
                    userId, accountId, category.categoryId(), batchId, row.amountPaise(), row.date(),
                    row.description(), category.merchant(), hash, now, now });
        }

        if (!inserts.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_TRANSACTION, inserts, INSERT_TYPES);
        }

        rowErrorRepository.saveAll(statement.errors().stream()
                .limit(MAX_STORED_ERRORS)
                .map(error -> new ImportRowError(batch, error.lineNumber(), error.message()))
                .toList());

        batch.setBankFormat(statement.formatName());
        batch.setRowsTotal(statement.totalRows());
        batch.setRowsImported(inserts.size());
        batch.setRowsDuplicate(duplicates);
        batch.setRowsFailed(statement.errors().size());
        batch.setStatus(ImportStatus.COMPLETED);
        batch.setCompletedAt(clock.instant());
    }

    private Set<String> existingHashes(long accountId, List<String> hashes) {
        Set<String> found = new HashSet<>();
        for (int from = 0; from < hashes.size(); from += HASH_LOOKUP_CHUNK) {
            List<String> chunk = hashes.subList(from, Math.min(from + HASH_LOOKUP_CHUNK, hashes.size()));
            found.addAll(namedJdbcTemplate.queryForList(EXISTING_HASHES,
                    Map.of("accountId", accountId, "hashes", chunk), String.class));
        }
        return found;
    }

    private void fail(long batchId, String message) {
        try {
            transactionTemplate.executeWithoutResult(status -> batchRepository.findById(batchId).ifPresent(batch -> {
                batch.setStatus(ImportStatus.FAILED);
                batch.setErrorMessage(message.length() <= 500 ? message : message.substring(0, 500));
                batch.setCompletedAt(clock.instant());
            }));
        } catch (RuntimeException e) {
            log.error("Couldn't record failure of import {}", batchId, e);
        }
    }
}
