package com.kaushiksridhar.finledger.split;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Works out each member's balance: positive means the group owes them, negative means they owe the group.
 *
 *   balance = paid for expenses - share of expenses + repayments sent - repayments received
 *
 * Every expense's shares add up to its amount and every repayment has a sender and a receiver,
 * so the balances in a group always add up to exactly zero.
 */
@Component
public class GroupBalanceCalculator {

    private final GroupExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;

    public GroupBalanceCalculator(GroupExpenseRepository expenseRepository, SettlementRepository settlementRepository) {
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
    }

    /**
     * Balances for the given members, in the same order. Pass a groupId for one group,
     * or null to work out every group the user has in four queries.
     */
    public Map<Long, Long> balances(long userId, Long groupId, Collection<GroupMember> members) {
        Map<Long, Long> paid = toMap(expenseRepository.paidByMember(userId, groupId));
        Map<Long, Long> owed = toMap(expenseRepository.owedByMember(userId, groupId));
        Map<Long, Long> sent = toMap(settlementRepository.sentByMember(userId, groupId));
        Map<Long, Long> received = toMap(settlementRepository.receivedByMember(userId, groupId));

        Map<Long, Long> balances = new LinkedHashMap<>();
        for (GroupMember member : members) {
            long id = member.getId();
            balances.put(id, paid.getOrDefault(id, 0L)
                    - owed.getOrDefault(id, 0L)
                    + sent.getOrDefault(id, 0L)
                    - received.getOrDefault(id, 0L));
        }
        return balances;
    }

    private static Map<Long, Long> toMap(List<MemberTotal> rows) {
        return rows.stream().collect(Collectors.toMap(MemberTotal::getMemberId, row -> row.getTotal().longValue()));
    }
}
