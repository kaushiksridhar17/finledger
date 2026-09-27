package com.kaushiksridhar.finledger.investment;

import java.util.List;

/** Everything a NAV source knows about one fund: its details and full NAV history. */
public record SchemeData(int schemeCode, String name, String fundHouse, String category, List<NavPoint> navs) {
}
