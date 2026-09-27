package com.kaushiksridhar.finledger.split;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.common.MoneyText;
import com.kaushiksridhar.finledger.notification.NotificationService;
import com.kaushiksridhar.finledger.notification.NotificationType;
import com.kaushiksridhar.finledger.split.DebtSimplifier.Transfer;
import com.kaushiksridhar.finledger.transaction.Transaction;

/**
 * Spots friends paying the user back. After any change to the ledger or to a group, it compares the
 * money that came into the user's accounts recently with what each friend owes them in the settle-up
 * plan. A credit with the right amount that names the friend becomes a suggestion (and a notification);
 * the user accepts it (recording the repayment, linked to the bank transaction) or dismisses it.
 */
@Service
public class PaymentMatchService {

    /** Only look at money that came in over the last 45 days. */
    static final int LOOKBACK_DAYS = 45;

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final PaymentMatchRepository matchRepository;
    private final SplitGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final GroupBalanceCalculator balanceCalculator;
    private final NotificationService notificationService;
    private final Clock clock;

    public PaymentMatchService(PaymentMatchRepository matchRepository,
            SplitGroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            GroupExpenseRepository expenseRepository,
            SettlementRepository settlementRepository,
            GroupBalanceCalculator balanceCalculator,
            NotificationService notificationService,
            Clock clock) {
        this.matchRepository = matchRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.balanceCalculator = balanceCalculator;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /** A friend is expected to pay the user this much, for a group whose first expense was on "since". */
    private record Expected(GroupMember member, long amountPaise, LocalDate since) {
    }

    /**
     * Brings the suggestions up to date. New matches are added (with a notification), and suggestions
     * that no longer fit (the friend settled some other way, an expense changed) are withdrawn.
     * Dismissed and accepted ones are never touched, so a "no" stays a "no".
     */
    @Transactional
    public void scan(long userId) {
        List<Expected> expected = expectedRepayments(userId);

        Map<String, PaymentMatch> existing = matchRepository.findAllForUser(userId).stream()
                .collect(Collectors.toMap(PaymentMatchService::keyOf, Function.identity()));
        Set<String> stillValid = new HashSet<>();

        if (!expected.isEmpty()) {
            LocalDate from = AppTime.today(clock).minusDays(LOOKBACK_DAYS);
            for (Transaction txn : matchRepository.unlinkedIncomingSince(userId, from)) {
                String text = txn.getDescription() + " " + (txn.getMerchant() == null ? "" : txn.getMerchant());
                List<Expected> hits = expected.stream()
                        .filter(e -> !txn.getTxnDate().isBefore(e.since()))
                        .filter(e -> PaymentMatchRules.amountMatches(txn.getAmountPaise(), e.amountPaise()))
                        .filter(e -> PaymentMatchRules.mentions(text, e.member().getName(), e.member().getUpiId()))
                        .toList();
                if (hits.size() != 1) {
                    continue;   // no match, or too ambiguous to guess (two friends called Rohan owing the same)
                }

                GroupMember member = hits.get(0).member();
                String key = member.getId() + ":" + txn.getId();
                stillValid.add(key);
                if (!existing.containsKey(key)) {
                    suggest(userId, member, txn);
                }
            }
        }

        for (Map.Entry<String, PaymentMatch> entry : existing.entrySet()) {
            PaymentMatch match = entry.getValue();
            if (match.getStatus() == MatchStatus.SUGGESTED && !stillValid.contains(entry.getKey())) {
                matchRepository.delete(match);
            }
        }
    }

    /** Records the repayment the suggestion describes, linked to the bank transaction. */
    @Transactional
    public void accept(long userId, long groupId, long matchId) {
        PaymentMatch match = getSuggestion(userId, groupId, matchId);
        GroupMember self = memberRepository.findByGroupIdOrderByIdAsc(groupId).stream()
                .filter(GroupMember::isSelf)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "This group has no owner"));

        Transaction txn = match.getTransaction();
        Settlement settlement = new Settlement();
        settlement.setGroup(match.getGroup());
        settlement.setFromMember(match.getMember());
        settlement.setToMember(self);
        settlement.setAmountPaise(txn.getAmountPaise());
        settlement.setSettledOn(txn.getTxnDate());
        settlement.setMethod(SettlementMethod.MATCHED);
        settlement.setTransaction(txn);
        settlement.setNote("Matched to a bank transaction");
        settlementRepository.save(settlement);

        match.setStatus(MatchStatus.ACCEPTED);
        settlementRepository.flush();
        scan(userId);
    }

    @Transactional
    public void dismiss(long userId, long groupId, long matchId) {
        getSuggestion(userId, groupId, matchId).setStatus(MatchStatus.DISMISSED);
    }

    /** For every group, the payments to the user in its settle-up plan. */
    private List<Expected> expectedRepayments(long userId) {
        List<GroupMember> allMembers = memberRepository.findAllForUser(userId);
        if (allMembers.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> balances = balanceCalculator.balances(userId, null, allMembers);
        Map<Long, List<GroupMember>> byGroup = allMembers.stream()
                .collect(Collectors.groupingBy(m -> m.getGroup().getId(), LinkedHashMap::new, Collectors.toList()));

        List<Expected> expected = new ArrayList<>();
        byGroup.forEach((groupId, members) -> {
            GroupMember self = members.stream().filter(GroupMember::isSelf).findFirst().orElse(null);
            LocalDate since = expenseRepository.firstExpenseDate(groupId);
            if (self == null || since == null) {
                return;
            }

            Map<Long, GroupMember> byId = members.stream().collect(Collectors.toMap(GroupMember::getId, Function.identity()));
            Map<Long, Long> groupBalances = new LinkedHashMap<>();
            members.forEach(m -> groupBalances.put(m.getId(), balances.getOrDefault(m.getId(), 0L)));

            for (Transfer transfer : DebtSimplifier.simplify(groupBalances)) {
                if (transfer.toId() == self.getId()) {
                    expected.add(new Expected(byId.get(transfer.fromId()), transfer.amountPaise(), since));
                }
            }
        });
        return expected;
    }

    private void suggest(long userId, GroupMember member, Transaction txn) {
        PaymentMatch match = new PaymentMatch();
        match.setGroup(member.getGroup());
        match.setMember(member);
        match.setTransaction(txn);
        match.setStatus(MatchStatus.SUGGESTED);
        matchRepository.save(match);

        SplitGroup group = member.getGroup();
        notificationService.notify(userId, NotificationType.SETTLEMENT_MATCH,
                "match:" + member.getId() + ":" + txn.getId(),
                member.getName() + " may have paid you back",
                MoneyText.format(txn.getAmountPaise()) + " came in on " + txn.getTxnDate().format(DAY_MONTH)
                        + ". Record it as their payment for " + group.getName() + "?",
                "/split/" + group.getId());
    }

    private PaymentMatch getSuggestion(long userId, long groupId, long matchId) {
        groupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Group not found"));
        return matchRepository.findInGroup(matchId, groupId)
                .filter(match -> match.getStatus() == MatchStatus.SUGGESTED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That suggestion isn't available any more"));
    }

    private static String keyOf(PaymentMatch match) {
        return match.getMember().getId() + ":" + match.getTransaction().getId();
    }
}
