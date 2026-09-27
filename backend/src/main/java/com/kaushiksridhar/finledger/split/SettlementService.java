package com.kaushiksridhar.finledger.split;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.common.ApiException;

/** Recording money paid back between members ("Mark as paid"), and undoing it. */
@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final PaymentMatchRepository matchRepository;
    private final GroupService groupService;
    private final PaymentMatchService paymentMatchService;

    public SettlementService(SettlementRepository settlementRepository,
            PaymentMatchRepository matchRepository,
            GroupService groupService,
            PaymentMatchService paymentMatchService) {
        this.settlementRepository = settlementRepository;
        this.matchRepository = matchRepository;
        this.groupService = groupService;
        this.paymentMatchService = paymentMatchService;
    }

    @Transactional
    public void create(long userId, long groupId, SettlementRequest request) {
        SplitGroup group = groupService.getOwned(userId, groupId);
        if (request.fromMemberId().equals(request.toMemberId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose two different people");
        }
        if (request.amountPaise() > ExpenseService.MAX_AMOUNT_PAISE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount is too large");
        }

        Settlement settlement = new Settlement();
        settlement.setGroup(group);
        settlement.setFromMember(groupService.getMember(groupId, request.fromMemberId()));
        settlement.setToMember(groupService.getMember(groupId, request.toMemberId()));
        settlement.setAmountPaise(request.amountPaise());
        settlement.setSettledOn(request.date());
        settlement.setMethod(SettlementMethod.MANUAL);
        settlement.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        settlementRepository.save(settlement);

        paymentMatchService.scan(userId);
    }

    /**
     * Undoes a repayment. If it came from a matched bank credit, that suggestion is marked dismissed
     * so the same credit isn't suggested again straight away.
     */
    @Transactional
    public void delete(long userId, long groupId, long settlementId) {
        groupService.getOwned(userId, groupId);
        Settlement settlement = settlementRepository.findByIdAndGroupId(settlementId, groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));

        if (settlement.getTransaction() != null) {
            matchRepository.findByMemberIdAndTransactionId(settlement.getFromMember().getId(), settlement.getTransaction().getId())
                    .ifPresent(match -> match.setStatus(MatchStatus.DISMISSED));
        }
        settlementRepository.delete(settlement);
        settlementRepository.flush();

        paymentMatchService.scan(userId);
    }
}
