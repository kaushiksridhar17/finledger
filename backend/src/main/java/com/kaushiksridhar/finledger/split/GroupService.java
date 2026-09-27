package com.kaushiksridhar.finledger.split;

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
import com.kaushiksridhar.finledger.split.DebtSimplifier.Transfer;
import com.kaushiksridhar.finledger.split.GroupExpenseRepository.GroupTotal;
import com.kaushiksridhar.finledger.user.User;
import com.kaushiksridhar.finledger.user.UserRepository;

/** Groups and the people in them, plus the full group view (balances, settle-up plan, activity). */
@Service
public class GroupService {

    /** The user plus up to 20 friends. */
    static final int MAX_MEMBERS = 21;

    private final SplitGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final PaymentMatchRepository matchRepository;
    private final GroupBalanceCalculator balanceCalculator;
    private final PaymentMatchService paymentMatchService;
    private final UserRepository userRepository;

    public GroupService(SplitGroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            GroupExpenseRepository expenseRepository,
            SettlementRepository settlementRepository,
            PaymentMatchRepository matchRepository,
            GroupBalanceCalculator balanceCalculator,
            PaymentMatchService paymentMatchService,
            UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.matchRepository = matchRepository;
        this.balanceCalculator = balanceCalculator;
        this.paymentMatchService = paymentMatchService;
        this.userRepository = userRepository;
    }

    // ------------------------------------------------------------------ reading

    /** Every group with the user's own balance in it, newest group first. */
    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> list(long userId) {
        List<SplitGroup> groups = groupRepository.findByUserIdOrderByIdDesc(userId);
        if (groups.isEmpty()) {
            return List.of();
        }

        List<GroupMember> members = memberRepository.findAllForUser(userId);
        Map<Long, Long> balances = balanceCalculator.balances(userId, null, members);
        Map<Long, Long> totals = expenseRepository.totalsByGroup(userId).stream()
                .collect(Collectors.toMap(GroupTotal::getGroupId, row -> row.getTotal().longValue()));
        Map<Long, List<GroupMember>> membersByGroup = members.stream()
                .collect(Collectors.groupingBy(m -> m.getGroup().getId(), LinkedHashMap::new, Collectors.toList()));

        return groups.stream()
                .map(group -> {
                    List<GroupMember> inGroup = membersByGroup.getOrDefault(group.getId(), List.of());
                    long myBalance = inGroup.stream()
                            .filter(GroupMember::isSelf)
                            .mapToLong(m -> balances.getOrDefault(m.getId(), 0L))
                            .sum();
                    List<String> friends = inGroup.stream()
                            .filter(m -> !m.isSelf())
                            .map(GroupMember::getName)
                            .toList();
                    return new GroupSummaryResponse(group.getId(), group.getName(), friends,
                            totals.getOrDefault(group.getId(), 0L), myBalance);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupDetailResponse detail(long userId, long groupId) {
        SplitGroup group = getOwned(userId, groupId);

        List<GroupMember> members = memberRepository.findByGroupIdOrderByIdAsc(groupId);
        Map<Long, GroupMember> byId = members.stream()
                .collect(Collectors.toMap(GroupMember::getId, Function.identity()));
        Map<Long, Long> balances = balanceCalculator.balances(userId, groupId, members);

        List<GroupExpense> expenses = expenseRepository.findByGroupIdOrderByExpenseDateDescIdDesc(groupId);
        List<Settlement> settlements = settlementRepository.findByGroupIdOrderBySettledOnDescIdDesc(groupId);
        List<PaymentMatch> suggestions = matchRepository.findByGroupAndStatus(groupId, MatchStatus.SUGGESTED);

        // Anyone who appears in an expense or a repayment has to stay, or the history would stop adding up
        Set<Long> involved = new HashSet<>();
        long totalSpent = 0;
        long myShare = 0;
        for (GroupExpense expense : expenses) {
            totalSpent += expense.getAmountPaise();
            involved.add(expense.getPaidBy().getId());
            for (ExpenseShare share : expense.getShares()) {
                involved.add(share.getMember().getId());
                if (share.getMember().isSelf()) {
                    myShare += share.getSharePaise();
                }
            }
        }
        for (Settlement settlement : settlements) {
            involved.add(settlement.getFromMember().getId());
            involved.add(settlement.getToMember().getId());
        }

        long myBalance = members.stream()
                .filter(GroupMember::isSelf)
                .mapToLong(m -> balances.getOrDefault(m.getId(), 0L))
                .sum();

        List<GroupDetailResponse.Member> memberViews = members.stream()
                .map(m -> new GroupDetailResponse.Member(m.getId(), m.getName(), m.getUpiId(), m.isSelf(),
                        balances.getOrDefault(m.getId(), 0L), !m.isSelf() && !involved.contains(m.getId())))
                .toList();

        List<GroupDetailResponse.Transfer> plan = DebtSimplifier.simplify(balances).stream()
                .map(t -> transferView(t, byId))
                .toList();

        List<GroupDetailResponse.Expense> expenseViews = expenses.stream().map(GroupService::expenseView).toList();
        List<GroupDetailResponse.Payment> paymentViews = settlements.stream().map(GroupService::paymentView).toList();
        List<GroupDetailResponse.Suggestion> suggestionViews = suggestions.stream()
                .map(m -> new GroupDetailResponse.Suggestion(m.getId(), m.getMember().getId(), m.getMember().getName(),
                        m.getTransaction().getAmountPaise(), m.getTransaction().getTxnDate(),
                        m.getTransaction().getDescription()))
                .toList();

        return new GroupDetailResponse(group.getId(), group.getName(), totalSpent, myShare, myBalance,
                memberViews, plan, expenseViews, paymentViews, suggestionViews);
    }

    // ------------------------------------------------------------------ groups

    @Transactional
    public long create(long userId, CreateGroupRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        List<String> names = new ArrayList<>();
        names.add(user.getName());
        for (MemberRequest friend : request.friends()) {
            names.add(friend.name().trim());
        }
        Set<String> distinct = names.stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        if (distinct.size() != names.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Everyone in a group needs a different name (you're already in it as " + user.getName() + ")");
        }

        SplitGroup group = new SplitGroup();
        group.setUser(user);
        group.setName(request.name().trim());
        groupRepository.save(group);

        memberRepository.save(new GroupMember(group, user.getName(), null, MemberKind.SELF));
        for (MemberRequest friend : request.friends()) {
            memberRepository.save(new GroupMember(group, friend.name().trim(), cleanUpi(friend.upiId()), MemberKind.FRIEND));
        }
        return group.getId();
    }

    @Transactional
    public void rename(long userId, long groupId, RenameGroupRequest request) {
        getOwned(userId, groupId).setName(request.name().trim());
    }

    /** Deletes the group with all its expenses and repayments (the database cascades the delete). */
    @Transactional
    public void delete(long userId, long groupId) {
        groupRepository.delete(getOwned(userId, groupId));
    }

    // ------------------------------------------------------------------ members

    @Transactional
    public void addMember(long userId, long groupId, MemberRequest request) {
        SplitGroup group = getOwned(userId, groupId);
        if (memberRepository.countByGroupId(groupId) >= MAX_MEMBERS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A group can have at most 20 friends");
        }
        String name = request.name().trim();
        if (memberRepository.existsByGroupIdAndName(groupId, name)) {
            throw nameTaken(name);
        }
        memberRepository.save(new GroupMember(group, name, cleanUpi(request.upiId()), MemberKind.FRIEND));
        paymentMatchService.scan(userId);
    }

    /** Changes a member's name or UPI ID. The user can set their own UPI ID here too, so friends can pay them. */
    @Transactional
    public void updateMember(long userId, long groupId, long memberId, MemberRequest request) {
        getOwned(userId, groupId);
        GroupMember member = getMember(groupId, memberId);
        String name = request.name().trim();
        if (memberRepository.existsByGroupIdAndNameAndIdNot(groupId, name, memberId)) {
            throw nameTaken(name);
        }
        member.setName(name);
        member.setUpiId(cleanUpi(request.upiId()));
        memberRepository.flush();
        paymentMatchService.scan(userId);
    }

    @Transactional
    public void removeMember(long userId, long groupId, long memberId) {
        getOwned(userId, groupId);
        GroupMember member = getMember(groupId, memberId);
        if (member.isSelf()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can't remove yourself from your own group");
        }
        boolean involved = expenseRepository.countByPaidById(memberId) > 0
                || expenseRepository.countSharesOf(memberId) > 0
                || settlementRepository.countInvolving(memberId) > 0;
        if (involved) {
            throw new ApiException(HttpStatus.CONFLICT,
                    member.getName() + " is part of some expenses or payments, so they can't be removed");
        }
        memberRepository.delete(member);
    }

    // ------------------------------------------------------------------ shared helpers

    public SplitGroup getOwned(long userId, long groupId) {
        return groupRepository.findByIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Group not found"));
    }

    public GroupMember getMember(long groupId, long memberId) {
        return memberRepository.findByIdAndGroupId(memberId, groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "That person isn't in this group"));
    }

    /** UPI IDs are case-insensitive, so they're stored in lower case. Blank means none. */
    static String cleanUpi(String upiId) {
        if (upiId == null || upiId.isBlank()) {
            return null;
        }
        return upiId.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException nameTaken(String name) {
        return new ApiException(HttpStatus.CONFLICT, "There's already someone called " + name + " in this group");
    }

    private static GroupDetailResponse.Transfer transferView(Transfer t, Map<Long, GroupMember> byId) {
        GroupMember from = byId.get(t.fromId());
        GroupMember to = byId.get(t.toId());
        return new GroupDetailResponse.Transfer(from.getId(), from.getName(), to.getId(), to.getName(), t.amountPaise());
    }

    private static GroupDetailResponse.Expense expenseView(GroupExpense e) {
        List<GroupDetailResponse.Share> shares = e.getShares().stream()
                .map(s -> new GroupDetailResponse.Share(s.getMember().getId(), s.getMember().getName(),
                        s.getSharePaise(), s.getInputValue()))
                .toList();
        return new GroupDetailResponse.Expense(e.getId(), e.getDescription(), e.getAmountPaise(), e.getExpenseDate(),
                e.getPaidBy().getId(), e.getPaidBy().getName(), e.getSplitType(), shares);
    }

    private static GroupDetailResponse.Payment paymentView(Settlement s) {
        return new GroupDetailResponse.Payment(s.getId(), s.getFromMember().getId(), s.getFromMember().getName(),
                s.getToMember().getId(), s.getToMember().getName(), s.getAmountPaise(), s.getSettledOn(),
                s.getMethod(), s.getNote());
    }
}
