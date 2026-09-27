package com.kaushiksridhar.finledger.importing;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kaushiksridhar.finledger.account.Account;
import com.kaushiksridhar.finledger.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One uploaded statement file and how its import went. */
@Entity
@Table(name = "import_batches")
@Getter
@Setter
@NoArgsConstructor
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "bank_format", length = 20)
    private String bankFormat;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ImportStatus status;

    @Column(name = "rows_total", nullable = false)
    private int rowsTotal;

    @Column(name = "rows_imported", nullable = false)
    private int rowsImported;

    @Column(name = "rows_duplicate", nullable = false)
    private int rowsDuplicate;

    @Column(name = "rows_failed", nullable = false)
    private int rowsFailed;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
