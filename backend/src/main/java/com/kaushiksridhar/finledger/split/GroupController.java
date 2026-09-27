package com.kaushiksridhar.finledger.split;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kaushiksridhar.finledger.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * Split groups. Every change returns the whole updated group (balances, settle-up plan, activity),
 * so the page just redraws from the response.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final SettlementService settlementService;
    private final PaymentMatchService paymentMatchService;

    public GroupController(GroupService groupService,
            ExpenseService expenseService,
            SettlementService settlementService,
            PaymentMatchService paymentMatchService) {
        this.groupService = groupService;
        this.expenseService = expenseService;
        this.settlementService = settlementService;
        this.paymentMatchService = paymentMatchService;
    }

    // ------------------------------------------------------------------ groups

    @GetMapping
    public List<GroupSummaryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return groupService.list(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDetailResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateGroupRequest request) {
        long userId = CurrentUser.id(jwt);
        long groupId = groupService.create(userId, request);
        return groupService.detail(userId, groupId);
    }

    @GetMapping("/{groupId}")
    public GroupDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId) {
        return groupService.detail(CurrentUser.id(jwt), groupId);
    }

    @PutMapping("/{groupId}")
    public GroupDetailResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId,
            @Valid @RequestBody RenameGroupRequest request) {
        long userId = CurrentUser.id(jwt);
        groupService.rename(userId, groupId, request);
        return groupService.detail(userId, groupId);
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId) {
        groupService.delete(CurrentUser.id(jwt), groupId);
    }

    // ------------------------------------------------------------------ members

    @PostMapping("/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDetailResponse addMember(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId,
            @Valid @RequestBody MemberRequest request) {
        long userId = CurrentUser.id(jwt);
        groupService.addMember(userId, groupId, request);
        return groupService.detail(userId, groupId);
    }

    @PutMapping("/{groupId}/members/{memberId}")
    public GroupDetailResponse updateMember(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId,
            @PathVariable("memberId") long memberId, @Valid @RequestBody MemberRequest request) {
        long userId = CurrentUser.id(jwt);
        groupService.updateMember(userId, groupId, memberId, request);
        return groupService.detail(userId, groupId);
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    public GroupDetailResponse removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId,
            @PathVariable("memberId") long memberId) {
        long userId = CurrentUser.id(jwt);
        groupService.removeMember(userId, groupId, memberId);
        return groupService.detail(userId, groupId);
    }

    // ------------------------------------------------------------------ expenses

    @PostMapping("/{groupId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDetailResponse addExpense(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId,
            @Valid @RequestBody ExpenseRequest request) {
        long userId = CurrentUser.id(jwt);
        expenseService.create(userId, groupId, request);
        return groupService.detail(userId, groupId);
    }

    @PutMapping("/{groupId}/expenses/{expenseId}")
    public GroupDetailResponse updateExpense(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId,
            @PathVariable("expenseId") long expenseId, @Valid @RequestBody ExpenseRequest request) {
        long userId = CurrentUser.id(jwt);
        expenseService.update(userId, groupId, expenseId, request);
        return groupService.detail(userId, groupId);
    }

    @DeleteMapping("/{groupId}/expenses/{expenseId}")
    public GroupDetailResponse deleteExpense(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId,
            @PathVariable("expenseId") long expenseId) {
        long userId = CurrentUser.id(jwt);
        expenseService.delete(userId, groupId, expenseId);
        return groupService.detail(userId, groupId);
    }

    // ------------------------------------------------------------------ repayments

    @PostMapping("/{groupId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDetailResponse addSettlement(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId,
            @Valid @RequestBody SettlementRequest request) {
        long userId = CurrentUser.id(jwt);
        settlementService.create(userId, groupId, request);
        return groupService.detail(userId, groupId);
    }

    @DeleteMapping("/{groupId}/settlements/{settlementId}")
    public GroupDetailResponse deleteSettlement(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId,
            @PathVariable("settlementId") long settlementId) {
        long userId = CurrentUser.id(jwt);
        settlementService.delete(userId, groupId, settlementId);
        return groupService.detail(userId, groupId);
    }

    /** "Yes, that was Rohan paying me back": records the repayment, linked to the bank transaction. */
    @PostMapping("/{groupId}/matches/{matchId}/accept")
    public GroupDetailResponse acceptMatch(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId,
            @PathVariable("matchId") long matchId) {
        long userId = CurrentUser.id(jwt);
        paymentMatchService.accept(userId, groupId, matchId);
        return groupService.detail(userId, groupId);
    }

    @PostMapping("/{groupId}/matches/{matchId}/dismiss")
    public GroupDetailResponse dismissMatch(@AuthenticationPrincipal Jwt jwt, @PathVariable("groupId") long groupId,
            @PathVariable("matchId") long matchId) {
        long userId = CurrentUser.id(jwt);
        paymentMatchService.dismiss(userId, groupId, matchId);
        return groupService.detail(userId, groupId);
    }
}
