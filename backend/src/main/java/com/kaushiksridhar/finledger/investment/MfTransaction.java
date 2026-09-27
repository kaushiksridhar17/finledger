package com.kaushiksridhar.finledger.investment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One purchase or redemption of a mutual fund. */
@Entity
@Table(name = "mf_transactions")
@Getter
@Setter
@NoArgsConstructor
public class MfTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Funds are public data kept in mf_schemes by SchemeCache, so this is just the code
    @Column(name = "scheme_code", nullable = false)
    private Integer schemeCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 4)
    private MfTxnType type;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal units;

    @Column(nullable = false, precision = 19, scale = 5)
    private BigDecimal nav;

    // Set when this purchase is a SIP debit from the bank statement
    @Column(name = "ledger_transaction_id")
    private Long ledgerTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sip_link_id")
    private SipLink sipLink;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
