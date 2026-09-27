package com.kaushiksridhar.finledger.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kaushiksridhar.finledger.split.DebtSimplifier.Transfer;

/** Plain unit tests of the settle-up plan. Ids: 1 = you, 2 = Rohan, 3 = Neha, 4 = Kabir. */
class DebtSimplifierTest {

    @Test
    @DisplayName("a chain of debts collapses: Neha owes Rohan and Rohan owes you, so Neha pays you directly")
    void chainCollapses() {
        // Neha owes Rohan 500 and Rohan owes you 500: Rohan's net position is zero
        List<Transfer> plan = DebtSimplifier.simplify(Map.of(1L, 50_000L, 2L, 0L, 3L, -50_000L));
        assertThat(plan).containsExactly(new Transfer(3, 1, 50_000));
    }

    @Test
    @DisplayName("six separate IOUs between four friends settle in three payments")
    void sixIousBecomeThree() {
        // Every pair owes something: 1->2 300, 1->3 200, 2->3 400, 2->4 100, 3->4 500, 4->1 250
        Map<Long, Long> balances = new HashMap<>();
        addDebt(balances, 1, 2, 30_000);
        addDebt(balances, 1, 3, 20_000);
        addDebt(balances, 2, 3, 40_000);
        addDebt(balances, 2, 4, 10_000);
        addDebt(balances, 3, 4, 50_000);
        addDebt(balances, 4, 1, 25_000);

        List<Transfer> plan = DebtSimplifier.simplify(balances);
        assertThat(plan).hasSize(3);
        assertSettlesEveryone(balances, plan);
    }

    @Test
    @DisplayName("people who owe exactly what someone else is owed are paired first")
    void exactMatchesFirst() {
        Map<Long, Long> balances = new LinkedHashMap<>();
        balances.put(1L, 70_000L);
        balances.put(2L, 30_000L);
        balances.put(3L, -30_000L);
        balances.put(4L, -70_000L);

        assertThat(DebtSimplifier.simplify(balances))
                .containsExactly(new Transfer(3, 2, 30_000), new Transfer(4, 1, 70_000));
    }

    @Test
    @DisplayName("a settled group needs no payments, and balances that don't add up are a bug")
    void edgeCases() {
        assertThat(DebtSimplifier.simplify(Map.of(1L, 0L, 2L, 0L))).isEmpty();
        assertThat(DebtSimplifier.simplify(Map.of())).isEmpty();
        assertThatThrownBy(() -> DebtSimplifier.simplify(Map.of(1L, 100L, 2L, -99L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("for a thousand random groups, the plan settles everyone in at most (people - 1) payments")
    void randomGroups() {
        Random random = new Random(11);
        for (int run = 0; run < 1_000; run++) {
            int people = 2 + random.nextInt(9);
            Map<Long, Long> balances = new HashMap<>();
            int ious = 1 + random.nextInt(20);
            for (int i = 0; i < ious; i++) {
                long from = 1 + random.nextInt(people);
                long to = 1 + random.nextInt(people);
                if (from != to) {
                    addDebt(balances, from, to, 1 + random.nextInt(500_000));
                }
            }

            List<Transfer> plan = DebtSimplifier.simplify(balances);
            long nonZero = balances.values().stream().filter(b -> b != 0).count();
            assertThat((long) plan.size()).isLessThanOrEqualTo(Math.max(0, nonZero - 1));
            assertSettlesEveryone(balances, plan);
        }
    }

    /** from owes to: from's balance goes down, to's goes up. */
    private static void addDebt(Map<Long, Long> balances, long from, long to, long amount) {
        balances.merge(from, -amount, Long::sum);
        balances.merge(to, amount, Long::sum);
    }

    private static void assertSettlesEveryone(Map<Long, Long> balances, List<Transfer> plan) {
        Map<Long, Long> after = new HashMap<>(balances);
        for (Transfer t : plan) {
            assertThat(t.amountPaise()).isPositive();
            after.merge(t.fromId(), t.amountPaise(), Long::sum);
            after.merge(t.toId(), -t.amountPaise(), Long::sum);
        }
        assertThat(after.values()).allMatch(b -> b == 0);
    }
}
