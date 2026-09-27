package com.kaushiksridhar.finledger.investment;

import java.util.List;

/**
 * Where fund details and NAVs come from. The real one is mfapi.in (MfApiNavSource); tests use a
 * fake one with fixed prices, so they never depend on the internet or on today's markets.
 */
public interface NavSource {

    List<SchemeHit> search(String query);

    /** Throws NavUnavailableException if the source can't be reached or has no such fund. */
    SchemeData fetch(int schemeCode);
}
