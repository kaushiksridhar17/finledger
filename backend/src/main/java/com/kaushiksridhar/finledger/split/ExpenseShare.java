package com.kaushiksridhar.finledger.split;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** How much of one expense a member owes. */
@Entity
@Table(name = "expense_shares")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private GroupExpense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private GroupMember member;

    @Column(name = "share_paise", nullable = false)
    private long sharePaise;

    // What the user typed for this person: paise for EXACT, basis points for PERCENT, a weight for SHARES
    @Column(name = "input_value")
    private Long inputValue;

    public ExpenseShare(GroupExpense expense, GroupMember member, long sharePaise, Long inputValue) {
        this.expense = expense;
        this.member = member;
        this.sharePaise = sharePaise;
        this.inputValue = inputValue;
    }
}
