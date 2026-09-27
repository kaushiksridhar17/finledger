package com.kaushiksridhar.finledger.split;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

/** One person in a group. Friends are just a name (and maybe a UPI ID); they don't need an account. */
@Entity
@Table(name = "group_members")
@Getter
@Setter
@NoArgsConstructor
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private SplitGroup group;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "upi_id", length = 100)
    private String upiId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private MemberKind kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public GroupMember(SplitGroup group, String name, String upiId, MemberKind kind) {
        this.group = group;
        this.name = name;
        this.upiId = upiId;
        this.kind = kind;
    }

    public boolean isSelf() {
        return kind == MemberKind.SELF;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
