package com.kaushiksridhar.finledger.split;

import java.util.List;

/** One group in the list: who's in it, how much was spent and where the user stands. */
public record GroupSummaryResponse(
        Long id,
        String name,
        List<String> friendNames,
        long totalSpentPaise,
        long myBalancePaise) {
}
