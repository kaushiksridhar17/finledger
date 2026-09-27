package com.kaushiksridhar.finledger.split;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.split.ExpenseRequest.ShareRequest;
import com.kaushiksridhar.finledger.split.SplitCalculator.Part;
import com.kaushiksridhar.finledger.split.SplitCalculator.SplitException;

/** Adding, changing and deleting shared expenses. Every change re-checks bank credits for repayments. */
@Service
public class ExpenseService {

    // Rs 100 crore in paise, the same ceiling as ledger transactions
    static final long MAX_AMOUNT_PAISE = 100_000_000_000L;

    private final GroupExpenseRepository expenseRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupService groupService;
    private final PaymentMatchService paymentMatchService;

    public ExpenseService(GroupExpenseRepository expenseRepository,
            GroupMemberRepository memberRepository,
            GroupService groupService,
            PaymentMatchService paymentMatchService) {
        this.expenseRepository = expenseRepository;
        this.memberRepository = memberRepository;
        this.groupService = groupService;
        this.paymentMatchService = paymentMatchService;
    }

    @Transactional
    public long create(long userId, long groupId, ExpenseRequest request) {
        SplitGroup group = groupService.getOwned(userId, groupId);
        GroupExpense expense = new GroupExpense();
        expense.setGroup(group);
        apply(expense, groupId, request);
        expenseRepository.save(expense);
        paymentMatchService.scan(userId);
        return expense.getId();
    }

    @Transactional
    public void update(long userId, long groupId, long expenseId, ExpenseRequest request) {
        groupService.getOwned(userId, groupId);
        GroupExpense expense = getExpense(groupId, expenseId);
        apply(expense, groupId, request);   // already managed: changes and new shares are saved at the next flush
        paymentMatchService.scan(userId);
    }

    @Transactional
    public void delete(long userId, long groupId, long expenseId) {
        groupService.getOwned(userId, groupId);
        expenseRepository.delete(getExpense(groupId, expenseId));
        expenseRepository.flush();
        paymentMatchService.scan(userId);
    }

    /** Checks the payer and everyone in the split belong to the group, works out the shares and copies it all onto the expense. */
    private void apply(GroupExpense expense, long groupId, ExpenseRequest request) {
        if (request.amountPaise() > MAX_AMOUNT_PAISE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount is too large");
        }

        Map<Long, GroupMember> members = memberRepository.findByGroupIdOrderByIdAsc(groupId).stream()
                .collect(Collectors.toMap(GroupMember::getId, Function.identity()));

        GroupMember payer = members.get(request.paidByMemberId());
        if (payer == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose who paid from the people in this group");
        }

        // Sorted by member id so rounding always favours the same people, whatever order the form sent
        List<ShareRequest> requested = request.shares().stream()
                .sorted(Comparator.comparing(ShareRequest::memberId))
                .toList();
        for (ShareRequest share : requested) {
            if (!members.containsKey(share.memberId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Everyone in the split must be in this group");
            }
        }

        List<Part> parts = requested.stream().map(s -> new Part(s.memberId(), s.value())).toList();
        List<Long> amounts;
        try {
            amounts = SplitCalculator.split(request.amountPaise(), request.splitType(), parts);
        } catch (SplitException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        expense.setPaidBy(payer);
        expense.setDescription(request.description().trim());
        expense.setAmountPaise(request.amountPaise());
        expense.setExpenseDate(request.date());
        expense.setSplitType(request.splitType());

        // Old shares are deleted first: otherwise Hibernate inserts the new ones before deleting the old,
        // and the (expense, member) unique key would reject them
        if (!expense.getShares().isEmpty()) {
            expense.getShares().clear();
            expenseRepository.flush();
        }

        boolean keepValues = request.splitType() != SplitType.EQUAL;
        for (int i = 0; i < parts.size(); i++) {
            long amount = amounts.get(i);
            if (amount > 0) {   // someone with a 0% or 0-share part isn't really in this expense
                expense.addShare(members.get(parts.get(i).memberId()), amount, keepValues ? parts.get(i).value() : null);
            }
        }
    }

    private GroupExpense getExpense(long groupId, long expenseId) {
        return expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Expense not found"));
    }
}
