package com.kaushiksridhar.finledger.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.kaushiksridhar.finledger.split.CreateGroupRequest;
import com.kaushiksridhar.finledger.split.ExpenseRequest;
import com.kaushiksridhar.finledger.split.ExpenseRequest.ShareRequest;
import com.kaushiksridhar.finledger.split.ExpenseService;
import com.kaushiksridhar.finledger.split.GroupDetailResponse;
import com.kaushiksridhar.finledger.split.GroupMember;
import com.kaushiksridhar.finledger.split.GroupMemberRepository;
import com.kaushiksridhar.finledger.split.GroupService;
import com.kaushiksridhar.finledger.split.MemberRequest;
import com.kaushiksridhar.finledger.split.PaymentMatchService;
import com.kaushiksridhar.finledger.split.SettlementRequest;
import com.kaushiksridhar.finledger.split.SettlementService;
import com.kaushiksridhar.finledger.split.SplitType;

/**
 * Two split groups for the demo user, dated relative to today:
 *  - "Goa trip" with three friends, every kind of split, one repayment already made, and a
 *    bank credit from Kabir that matches what he owes, so a "may have paid you back" suggestion appears
 *  - "Flat 402" with a flatmate the user owes money to, with a UPI ID so "Pay with UPI" works
 * Everything goes through the normal services, so the demo follows the same rules as real data.
 */
@Component
public class DemoSplitGroups {

    private static final String INSERT_CREDIT = """
            INSERT INTO transactions
                (user_id, account_id, category_id, amount_paise, txn_date, description, merchant, notes, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?)
            """;

    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final SettlementService settlementService;
    private final PaymentMatchService paymentMatchService;
    private final GroupMemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;

    public DemoSplitGroups(GroupService groupService,
            ExpenseService expenseService,
            SettlementService settlementService,
            PaymentMatchService paymentMatchService,
            GroupMemberRepository memberRepository,
            JdbcTemplate jdbcTemplate) {
        this.groupService = groupService;
        this.expenseService = expenseService;
        this.settlementService = settlementService;
        this.paymentMatchService = paymentMatchService;
        this.memberRepository = memberRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(long userId, long bankAccountId, Long transferCategoryId, LocalDate today) {
        createGoaTrip(userId, bankAccountId, transferCategoryId, today);
        createFlat(userId, today);
    }

    private void createGoaTrip(long userId, long bankAccountId, Long transferCategoryId, LocalDate today) {
        long groupId = groupService.create(userId, new CreateGroupRequest("Goa trip", List.of(
                new MemberRequest("Rohan Kulkarni", "rohan.k@okaxis"),
                new MemberRequest("Neha Sharma", "neha.sharma@oksbi"),
                new MemberRequest("Kabir Singh", null))));

        Map<String, Long> ids = memberIds(groupId);
        long me = ids.get("me");
        long rohan = ids.get("Rohan Kulkarni");
        long neha = ids.get("Neha Sharma");
        long kabir = ids.get("Kabir Singh");
        List<Long> everyone = List.of(me, rohan, neha, kabir);

        expense(userId, groupId, "Flights to Goa", 2_400_000, today.minusDays(24), me, SplitType.EQUAL, equal(everyone));
        expense(userId, groupId, "Villa in Anjuna, 3 nights", 1_800_000, today.minusDays(23), rohan, SplitType.EQUAL,
                equal(everyone));
        expense(userId, groupId, "Dinner at Thalassa", 640_000, today.minusDays(22), neha, SplitType.EQUAL, equal(everyone));
        // Neha didn't ride a scooter
        expense(userId, groupId, "Scooter rentals", 240_000, today.minusDays(22), kabir, SplitType.SHARES,
                List.of(share(me, 1), share(rohan, 1), share(kabir, 1)));
        // Neha skipped parasailing
        expense(userId, groupId, "Parasailing", 750_000, today.minusDays(21), me, SplitType.EXACT,
                List.of(share(me, 250_000), share(rohan, 250_000), share(kabir, 250_000)));
        expense(userId, groupId, "Groceries and drinks", 360_000, today.minusDays(21), rohan, SplitType.PERCENT,
                List.of(share(me, 2_500), share(rohan, 2_500), share(neha, 2_500), share(kabir, 2_500)));

        settlementService.create(userId, groupId,
                new SettlementRequest(kabir, me, 500_000L, today.minusDays(10), "Cash at the airport"));

        // Kabir sends the rest over UPI: it lands in the bank account and should be spotted as his repayment
        GroupDetailResponse trip = groupService.detail(userId, groupId);
        trip.settleUp().stream()
                .filter(t -> t.fromMemberId() == kabir && t.toMemberId() == me)
                .findFirst()
                .ifPresent(t -> addBankCredit(userId, bankAccountId, transferCategoryId, t.amountPaise(), today.minusDays(2),
                        "UPI/CR/427812345678/KABIR SINGH/okicici/Goa trip"));
        paymentMatchService.scan(userId);
    }

    private void createFlat(long userId, LocalDate today) {
        long groupId = groupService.create(userId, new CreateGroupRequest("Flat 402", List.of(
                new MemberRequest("Priya Nair", "priya.nair@okhdfcbank"))));

        Map<String, Long> ids = memberIds(groupId);
        long me = ids.get("me");
        long priya = ids.get("Priya Nair");
        List<Long> both = List.of(me, priya);

        expense(userId, groupId, "Electricity bill", 284_000, today.minusDays(12), priya, SplitType.EQUAL, equal(both));
        expense(userId, groupId, "Cook's salary", 600_000, today.minusDays(5), priya, SplitType.EQUAL, equal(both));
        expense(userId, groupId, "Groceries from BigBasket", 186_000, today.minusDays(3), me, SplitType.EQUAL, equal(both));
    }

    /** Member ids by name, with the user themselves under "me". */
    private Map<String, Long> memberIds(long groupId) {
        return memberRepository.findByGroupIdOrderByIdAsc(groupId).stream()
                .collect(Collectors.toMap(m -> m.isSelf() ? "me" : m.getName(), GroupMember::getId));
    }

    private void expense(long userId, long groupId, String description, long amountPaise, LocalDate date,
            long paidBy, SplitType type, List<ShareRequest> shares) {
        expenseService.create(userId, groupId, new ExpenseRequest(description, amountPaise, date, paidBy, type, shares));
    }

    private static List<ShareRequest> equal(List<Long> memberIds) {
        return memberIds.stream().map(id -> new ShareRequest(id, null)).toList();
    }

    private static ShareRequest share(long memberId, long value) {
        return new ShareRequest(memberId, value);
    }

    private void addBankCredit(long userId, long accountId, Long categoryId, long amountPaise, LocalDate date,
            String description) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(INSERT_CREDIT, userId, accountId, categoryId, amountPaise, date, description, now, now);
    }
}
