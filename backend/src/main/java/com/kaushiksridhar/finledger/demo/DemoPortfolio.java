package com.kaushiksridhar.finledger.demo;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kaushiksridhar.finledger.investment.FundTransactionRequest;
import com.kaushiksridhar.finledger.investment.InvestmentService;
import com.kaushiksridhar.finledger.investment.MfTxnType;
import com.kaushiksridhar.finledger.investment.MfWarmup;
import com.kaushiksridhar.finledger.investment.SchemeCache;
import com.kaushiksridhar.finledger.investment.SipDetector;
import com.kaushiksridhar.finledger.investment.SipLinkRequest;

/**
 * The demo user's mutual funds, using real NAVs:
 *  - their monthly "NACH/BSE STARMF/SIP PARAG PARIKH FLEXI CAP" debits linked to that fund, so every
 *    instalment in the year of history becomes a purchase
 *  - a Rs 50,000 lump sum in a Nifty 50 index fund about ten months ago
 * The funds are fetched before the demo's database transaction starts (prefetchFunds, via DemoService.prepare).
 * If mfapi.in can't be reached and they aren't cached, the portfolio is skipped: the demo still works, and
 * the Investments page offers the SIP as a suggestion instead.
 */
@Component
public class DemoPortfolio {

    static final String SIP_DESCRIPTION = "NACH/BSE STARMF/SIP PARAG PARIKH FLEXI CAP";

    private static final Logger log = LoggerFactory.getLogger(DemoPortfolio.class);

    private final SchemeCache schemeCache;
    private final InvestmentService investmentService;

    public DemoPortfolio(SchemeCache schemeCache, InvestmentService investmentService) {
        this.schemeCache = schemeCache;
        this.investmentService = investmentService;
    }

    /** Makes sure both funds are cached. Called outside any transaction (DemoService.prepare); never throws. */
    public void prefetchFunds() {
        for (Integer code : MfWarmup.DEMO_FUNDS) {
            try {
                schemeCache.ensure(code);
            } catch (RuntimeException e) {
                log.info("Couldn't fetch fund {} for the demo: {}", code, e.getMessage());
            }
        }
    }

    /** Builds the portfolio from cached NAVs only; skips it if they aren't there. Runs inside the demo's transaction. */
    public void create(long userId, LocalDate today) {
        int flexiCap = MfWarmup.DEMO_FUNDS.get(0);
        int nifty = MfWarmup.DEMO_FUNDS.get(1);
        LocalDate lumpSumDate = today.minusDays(300);

        // Only go ahead if the NAVs are readable from here, so the steps below can't fail
        boolean navsReady = isCached(flexiCap) && isCached(nifty)
                && schemeCache.series(nifty, lumpSumDate.minusDays(10), lumpSumDate.plusDays(10)).tradeOn(lumpSumDate).isPresent()
                && !schemeCache.series(flexiCap, today.minusYears(1), today).isEmpty();
        if (!navsReady) {
            log.info("Demo portfolio skipped: fund prices aren't available");
            return;
        }

        investmentService.linkSip(userId, new SipLinkRequest(SipDetector.key(SIP_DESCRIPTION), flexiCap));
        investmentService.addTransaction(userId,
                new FundTransactionRequest(nifty, MfTxnType.BUY, lumpSumDate, 5_000_000L, null));
    }

    private boolean isCached(int schemeCode) {
        return schemeCache.find(schemeCode).map(info -> info.latestNav() != null).orElse(false);
    }
}
