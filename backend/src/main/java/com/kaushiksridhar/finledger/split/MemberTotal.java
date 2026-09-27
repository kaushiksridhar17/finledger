package com.kaushiksridhar.finledger.split;

/** One row of a "sum per member" query. */
public interface MemberTotal {

    Long getMemberId();

    Number getTotal();
}
