package com.kaushiksridhar.finledger.investment;

import java.time.Instant;

import com.kaushiksridhar.finledger.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** "Bank debits that look like this are my SIP in this fund." Matching transactions become purchases. */
@Entity
@Table(name = "mf_sip_links")
@Getter
@Setter
@NoArgsConstructor
public class SipLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "scheme_code", nullable = false)
    private Integer schemeCode;

    // SipDetector.key() of the debit's description
    @Column(name = "match_key", nullable = false, length = 120)
    private String matchKey;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
