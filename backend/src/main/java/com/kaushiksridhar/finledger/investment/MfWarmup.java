package com.kaushiksridhar.finledger.investment;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Fetches the demo's funds in the background when the app starts, so the first "Try the demo" click
 * doesn't wait for mfapi.in. Nothing breaks if it fails: the demo then fetches them itself, or
 * shows the SIP suggestion instead of a portfolio if mfapi.in is down.
 */
@Component
public class MfWarmup {

    /** Parag Parikh Flexi Cap and UTI Nifty 50 Index, both Direct Plan - Growth. */
    public static final List<Integer> DEMO_FUNDS = List.of(122639, 120716);

    private static final Logger log = LoggerFactory.getLogger(MfWarmup.class);

    private final SchemeCache schemeCache;
    private final boolean enabled;

    public MfWarmup(SchemeCache schemeCache,
            @Value("${finledger.mf.warmup:true}") boolean warmup,
            @Value("${finledger.demo.enabled:true}") boolean demoEnabled) {
        this.schemeCache = schemeCache;
        this.enabled = warmup && demoEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!enabled) {
            return;
        }
        Thread.startVirtualThread(() -> {
            for (Integer code : DEMO_FUNDS) {
                try {
                    schemeCache.ensure(code);
                } catch (RuntimeException e) {
                    log.info("Couldn't pre-fetch fund {} for the demo: {}", code, e.getMessage());
                }
            }
        });
    }
}
