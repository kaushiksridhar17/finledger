package com.kaushiksridhar.finledger.investment;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A fund's net asset value (price of one unit, in rupees) on one day. */
public record NavPoint(LocalDate date, BigDecimal nav) {
}
