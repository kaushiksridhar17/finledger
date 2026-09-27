package com.kaushiksridhar.finledger.split;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the group page shows. Every change to a group returns this, so the page can
 * redraw from one response instead of reloading several things.
 */
public record GroupDetailResponse(
        Long id,
        String name,
        long totalSpentPaise,
        long mySharePaise,
        long myBalancePaise,
        List<Member> members,
        List<Transfer> settleUp,
        List<Expense> expenses,
        List<Payment> settlements,
        List<Suggestion> suggestions) {

    /** balancePaise: positive = the group owes them, negative = they owe the group. */
    public record Member(Long id, String name, String upiId, boolean self, long balancePaise, boolean removable) {
    }

    /** One payment in the settle-up plan. */
    public record Transfer(Long fromMemberId, String fromName, Long toMemberId, String toName, long amountPaise) {
    }

    public record Expense(
            Long id,
            String description,
            long amountPaise,
            LocalDate date,
            Long paidByMemberId,
            String paidByName,
            SplitType splitType,
            List<Share> shares) {
    }

    /** value is what was typed: paise (EXACT), basis points (PERCENT), a weight (SHARES), null for EQUAL. */
    public record Share(Long memberId, String memberName, long sharePaise, Long value) {
    }

    public record Payment(
            Long id,
            Long fromMemberId,
            String fromName,
            Long toMemberId,
            String toName,
            long amountPaise,
            LocalDate date,
            SettlementMethod method,
            String note) {
    }

    /** A bank credit that looks like this friend paying the user back. */
    public record Suggestion(
            Long id,
            Long memberId,
            String memberName,
            long amountPaise,
            LocalDate date,
            String description) {
    }
}
