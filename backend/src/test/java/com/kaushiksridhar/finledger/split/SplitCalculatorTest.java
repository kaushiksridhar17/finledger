package com.kaushiksridhar.finledger.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kaushiksridhar.finledger.split.SplitCalculator.Part;
import com.kaushiksridhar.finledger.split.SplitCalculator.SplitException;

/** Plain unit tests: splitting always gives whole paise that add up to the total exactly. */
class SplitCalculatorTest {

    @Test
    @DisplayName("an equal split that doesn't divide evenly gives the spare paise to the first people")
    void equalSplitWithRemainder() {
        // Rs 100 between 3 people: 33.34 + 33.33 + 33.33
        assertThat(SplitCalculator.split(10_000, SplitType.EQUAL, parts(1, 2, 3)))
                .containsExactly(3_334L, 3_333L, 3_333L);
        // Rs 1,000.01 between 4: one extra paisa for the first person
        assertThat(SplitCalculator.split(100_001, SplitType.EQUAL, parts(1, 2, 3, 4)))
                .containsExactly(25_001L, 25_000L, 25_000L, 25_000L);
    }

    @Test
    @DisplayName("exact amounts must add up to the expense")
    void exactAmounts() {
        List<Part> ok = List.of(new Part(1, 150_000L), new Part(2, 50_000L), new Part(3, 0L));
        assertThat(SplitCalculator.split(200_000, SplitType.EXACT, ok)).containsExactly(150_000L, 50_000L, 0L);

        List<Part> short1 = List.of(new Part(1, 150_000L), new Part(2, 40_000L));
        assertThatThrownBy(() -> SplitCalculator.split(200_000, SplitType.EXACT, short1))
                .isInstanceOf(SplitException.class)
                .hasMessage("The amounts add up to \u20B91,900, but the expense is \u20B92,000");
    }

    @Test
    @DisplayName("percentages use the largest remainder so the shares still add up exactly")
    void percentages() {
        // 33.33% + 33.33% + 33.34% of Rs 99.99
        List<Part> thirds = List.of(new Part(1, 3_333L), new Part(2, 3_333L), new Part(3, 3_334L));
        List<Long> shares = SplitCalculator.split(9_999, SplitType.PERCENT, thirds);
        assertThat(shares).containsExactly(3_333L, 3_333L, 3_333L);

        List<Part> ninety = List.of(new Part(1, 5_000L), new Part(2, 4_000L));
        assertThatThrownBy(() -> SplitCalculator.split(10_000, SplitType.PERCENT, ninety))
                .isInstanceOf(SplitException.class)
                .hasMessage("The percentages add up to 90% instead of 100%");
    }

    @Test
    @DisplayName("shares split in proportion: 2 nights and 1 night of a Rs 3,000 stay is Rs 2,000 and Rs 1,000")
    void weightedShares() {
        List<Part> nights = List.of(new Part(1, 2L), new Part(2, 1L), new Part(3, 0L));
        assertThat(SplitCalculator.split(300_000, SplitType.SHARES, nights)).containsExactly(200_000L, 100_000L, 0L);

        List<Part> none = List.of(new Part(1, 0L), new Part(2, 0L));
        assertThatThrownBy(() -> SplitCalculator.split(300_000, SplitType.SHARES, none))
                .isInstanceOf(SplitException.class)
                .hasMessage("Give at least one person a share");
    }

    @Test
    @DisplayName("bad input is refused with a message the user can read")
    void invalidInput() {
        assertThatThrownBy(() -> SplitCalculator.split(0, SplitType.EQUAL, parts(1, 2)))
                .isInstanceOf(SplitException.class)
                .hasMessage("The amount must be more than zero");
        assertThatThrownBy(() -> SplitCalculator.split(1_000, SplitType.EQUAL, List.of()))
                .isInstanceOf(SplitException.class)
                .hasMessage("Choose who this expense is split between");
        assertThatThrownBy(() -> SplitCalculator.split(1_000, SplitType.EQUAL, parts(1, 1)))
                .isInstanceOf(SplitException.class)
                .hasMessage("Each person can only appear once in a split");
        assertThatThrownBy(() -> SplitCalculator.split(1_000, SplitType.EXACT, List.of(new Part(1, null))))
                .isInstanceOf(SplitException.class)
                .hasMessage("Enter an amount for everyone in the split");
    }

    @Test
    @DisplayName("for a thousand random splits, the shares always add up to the total and differ by at most a paisa from the exact fraction")
    void randomSplitsAlwaysAddUp() {
        Random random = new Random(7);
        for (int run = 0; run < 1_000; run++) {
            long total = 1 + random.nextInt(10_000_000);
            int people = 1 + random.nextInt(8);
            List<Part> parts = new ArrayList<>();
            long weightSum = 0;
            for (int i = 0; i < people; i++) {
                long weight = random.nextInt(6);
                weightSum += weight;
                parts.add(new Part(i + 1, weight));
            }
            if (weightSum == 0) {
                continue;
            }

            List<Long> shares = SplitCalculator.split(total, SplitType.SHARES, parts);
            assertThat(shares.stream().mapToLong(Long::longValue).sum()).isEqualTo(total);
            for (int i = 0; i < people; i++) {
                double exact = (double) total * parts.get(i).value() / weightSum;
                assertThat(Math.abs(shares.get(i) - exact)).isLessThan(1.0);
            }
        }
    }

    @Test
    @DisplayName("percentages are described the way people write them")
    void percentText() {
        assertThat(SplitCalculator.percentText(9_000)).isEqualTo("90");
        assertThat(SplitCalculator.percentText(3_333)).isEqualTo("33.33");
        assertThat(SplitCalculator.percentText(3_350)).isEqualTo("33.5");
        assertThat(SplitCalculator.percentText(5)).isEqualTo("0.05");
    }

    private static List<Part> parts(long... memberIds) {
        List<Part> parts = new ArrayList<>();
        for (long id : memberIds) {
            parts.add(new Part(id, null));
        }
        return parts;
    }
}
