package com.kaushiksridhar.finledger.split;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * Turns everyone's balance into a short list of payments that settles the whole group.
 *
 * Only each person's net position matters: if Neha owes Rohan Rs 500 and Rohan owes you Rs 500,
 * Neha can pay you directly and Rohan doesn't need to be involved. So instead of repaying every
 * expense one by one, the group needs at most (people - 1) payments.
 *
 * 1. Anyone who owes exactly what someone else is owed pays them directly (one payment clears two people).
 * 2. Then, repeatedly, the person who owes the most pays the person who is owed the most, as much as possible.
 *
 * Finding the true minimum number of payments is NP-hard in general; this greedy approach is the
 * standard practical answer, never needs more than (people - 1) payments, and is usually optimal
 * for real groups. Ties are broken by member id, so the same balances always give the same plan.
 */
public final class DebtSimplifier {

    private DebtSimplifier() {
    }

    /** fromId pays toId amountPaise. */
    public record Transfer(long fromId, long toId, long amountPaise) {
    }

    /**
     * @param balances member id to net balance in paise: positive = the group owes them,
     *                 negative = they owe the group. Must add up to zero.
     */
    public static List<Transfer> simplify(Map<Long, Long> balances) {
        long sum = 0;
        for (long balance : balances.values()) {
            sum = Math.addExact(sum, balance);
        }
        if (sum != 0) {
            throw new IllegalArgumentException("Balances must add up to zero, but they add up to " + sum);
        }

        // Sorted by id so the result never depends on HashMap ordering
        TreeMap<Long, Long> creditors = new TreeMap<>();
        TreeMap<Long, Long> debtors = new TreeMap<>();
        balances.forEach((id, balance) -> {
            if (balance > 0) {
                creditors.put(id, balance);
            } else if (balance < 0) {
                debtors.put(id, -balance);
            }
        });

        List<Transfer> transfers = new ArrayList<>();
        payExactMatches(debtors, creditors, transfers);
        payGreedily(debtors, creditors, transfers);
        return transfers;
    }

    private static void payExactMatches(TreeMap<Long, Long> debtors, TreeMap<Long, Long> creditors, List<Transfer> out) {
        Iterator<Map.Entry<Long, Long>> it = debtors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> debtor = it.next();
            Long creditorId = null;
            for (Map.Entry<Long, Long> creditor : creditors.entrySet()) {
                if (creditor.getValue().equals(debtor.getValue())) {
                    creditorId = creditor.getKey();
                    break;
                }
            }
            if (creditorId != null) {
                out.add(new Transfer(debtor.getKey(), creditorId, debtor.getValue()));
                creditors.remove(creditorId);
                it.remove();
            }
        }
    }

    private static void payGreedily(TreeMap<Long, Long> debtors, TreeMap<Long, Long> creditors, List<Transfer> out) {
        // [member id, amount], biggest amount first, then lowest id
        Comparator<long[]> biggestFirst = Comparator.<long[]>comparingLong(entry -> -entry[1])
                .thenComparingLong(entry -> entry[0]);
        PriorityQueue<long[]> owed = new PriorityQueue<>(biggestFirst);
        PriorityQueue<long[]> owing = new PriorityQueue<>(biggestFirst);
        creditors.forEach((id, amount) -> owed.add(new long[] { id, amount }));
        debtors.forEach((id, amount) -> owing.add(new long[] { id, amount }));

        while (!owed.isEmpty() && !owing.isEmpty()) {
            long[] creditor = owed.poll();
            long[] debtor = owing.poll();
            long amount = Math.min(creditor[1], debtor[1]);
            out.add(new Transfer(debtor[0], creditor[0], amount));

            if (creditor[1] > amount) {
                owed.add(new long[] { creditor[0], creditor[1] - amount });
            }
            if (debtor[1] > amount) {
                owing.add(new long[] { debtor[0], debtor[1] - amount });
            }
        }
    }
}
