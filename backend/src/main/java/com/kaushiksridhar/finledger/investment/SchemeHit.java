package com.kaushiksridhar.finledger.investment;

/** One search result: a fund's scheme code and full name, e.g. 122639, "Parag Parikh Flexi Cap Fund - Direct Plan - Growth". */
public record SchemeHit(int schemeCode, String name) {
}
