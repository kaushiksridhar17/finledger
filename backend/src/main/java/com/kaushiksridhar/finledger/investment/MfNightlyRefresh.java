package com.kaushiksridhar.finledger.investment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AMFI publishes each day's NAVs by about 11 pm, so at 11:40 pm Indian time this fetches the new NAVs
 * for every fund anyone uses, then turns SIP debits that were waiting for today's NAV into purchases.
 * One fund or user failing doesn't stop the rest.
 */
@Component
public class MfNightlyRefresh {

    private static final Logger log = LoggerFactory.getLogger(MfNightlyRefresh.class);

    private final SchemeCache schemeCache;
    private final SipLinkRepository sipLinkRepository;
    private final InvestmentService investmentService;

    public MfNightlyRefresh(SchemeCache schemeCache, SipLinkRepository sipLinkRepository, InvestmentService investmentService) {
        this.schemeCache = schemeCache;
        this.sipLinkRepository = sipLinkRepository;
        this.investmentService = investmentService;
    }

    @Scheduled(cron = "0 40 23 * * *", zone = "Asia/Kolkata")
    public void refresh() {
        int failed = 0;
        for (Integer code : schemeCache.codesInUse()) {
            try {
                schemeCache.refresh(code);
            } catch (RuntimeException e) {
                failed++;
                log.warn("Couldn't refresh NAVs for scheme {}: {}", code, e.getMessage());
            }
        }

        for (Long userId : sipLinkRepository.userIdsWithLinks()) {
            try {
                investmentService.syncSips(userId);
            } catch (RuntimeException e) {
                log.warn("Couldn't sync SIP purchases for user {}", userId, e);
            }
        }
        log.info("Nightly NAV refresh done, {} fund(s) failed", failed);
    }
}
