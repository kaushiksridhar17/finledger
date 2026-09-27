package com.kaushiksridhar.finledger.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kaushiksridhar.finledger.demo.DemoDataGenerator;
import com.kaushiksridhar.finledger.recurring.RecurringDetector.Detected;
import com.kaushiksridhar.finledger.recurring.RecurringDetector.TxnPoint;

/** Plain unit tests of the detector: no Spring, no database, a fixed "today". */
class RecurringDetectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    @Test
    @DisplayName("four monthly Netflix charges are a monthly subscription, next due a month after the last")
    void monthlySubscription() {
        List<Detected> found = RecurringDetector.detect(List.of(
                out("2026-06-15", 64_900, "NETFLIX.COM", "Netflix"),
                out("2026-07-15", 64_900, "NETFLIX.COM", "Netflix"),
                out("2026-08-15", 64_900, "NETFLIX.COM", "Netflix"),
                out("2026-09-15", 64_900, "NETFLIX.COM", "Netflix")), TODAY);

        assertThat(found).hasSize(1);
        Detected netflix = found.get(0);
        assertThat(netflix.name()).isEqualTo("Netflix");
        assertThat(netflix.frequency()).isEqualTo(Frequency.MONTHLY);
        assertThat(netflix.direction()).isEqualTo(Direction.OUT);
        assertThat(netflix.amountPaise()).isEqualTo(64_900);
        assertThat(netflix.occurrences()).isEqualTo(4);
        assertThat(netflix.nextDue()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    @DisplayName("reference numbers and bank jargon are ignored when grouping descriptions")
    void descriptionKeyIgnoresReferences() {
        List<Detected> found = RecurringDetector.detect(List.of(
                out("2026-07-05", 2_200_000, "UPI/RENT/R KUMAR/428112345673", null),
                out("2026-08-05", 2_200_000, "UPI/RENT/R KUMAR/428212399999", null),
                out("2026-09-05", 2_200_000, "UPI/RENT/R KUMAR/428312300001", null)), TODAY);

        assertThat(found).extracting(Detected::name).containsExactly("Rent R Kumar");
        assertThat(RecurringDetector.cleanDescription("SALARY ACME TECH PVT LTD AUG2026"))
                .isEqualTo("SALARY ACME TECH PVT LTD");
    }

    @Test
    @DisplayName("a bill whose amount varies (electricity) still counts, with its range recorded")
    void variableAmount() {
        List<Detected> found = RecurringDetector.detect(List.of(
                out("2026-06-08", 118_000, "BESCOM ELECTRICITY", "BESCOM"),
                out("2026-07-08", 224_000, "BESCOM ELECTRICITY", "BESCOM"),
                out("2026-08-08", 184_000, "BESCOM ELECTRICITY", "BESCOM"),
                out("2026-09-08", 161_000, "BESCOM ELECTRICITY", "BESCOM")), TODAY);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).minAmountPaise()).isEqualTo(118_000);
        assertThat(found.get(0).maxAmountPaise()).isEqualTo(224_000);
    }

    @Test
    @DisplayName("amounts that differ wildly are not one recurring payment")
    void wildAmounts() {
        assertThat(RecurringDetector.detect(List.of(
                out("2026-07-01", 50_000, "AMAZON", "Amazon"),
                out("2026-08-01", 900_000, "AMAZON", "Amazon"),
                out("2026-09-01", 120_000, "AMAZON", "Amazon")), TODAY)).isEmpty();
    }

    @Test
    @DisplayName("frequent, irregular spending like food delivery is not a subscription")
    void irregular() {
        List<TxnPoint> swiggy = new ArrayList<>();
        int[] days = { 1, 3, 4, 9, 12, 13, 20, 22, 27 };
        for (int month = 6; month <= 9; month++) {
            for (int day : days) {
                swiggy.add(out("2026-%02d-%02d".formatted(month, day), 43_200, "SWIGGY", "Swiggy"));
            }
        }
        assertThat(RecurringDetector.detect(swiggy, TODAY)).isEmpty();
    }

    @Test
    @DisplayName("two monthly payments aren't enough, but two yearly ones are")
    void minimumOccurrences() {
        assertThat(RecurringDetector.detect(List.of(
                out("2026-08-15", 64_900, "NETFLIX", "Netflix"),
                out("2026-09-15", 64_900, "NETFLIX", "Netflix")), TODAY)).isEmpty();

        List<Detected> yearly = RecurringDetector.detect(List.of(
                out("2025-03-01", 149_900, "AMAZON PRIME", "Amazon Prime"),
                out("2026-03-01", 149_900, "AMAZON PRIME", "Amazon Prime")), TODAY);
        assertThat(yearly).extracting(Detected::frequency).containsExactly(Frequency.YEARLY);
    }

    @Test
    @DisplayName("a subscription that stopped months ago is not reported")
    void stopped() {
        assertThat(RecurringDetector.detect(List.of(
                out("2026-02-15", 64_900, "NETFLIX", "Netflix"),
                out("2026-03-15", 64_900, "NETFLIX", "Netflix"),
                out("2026-04-15", 64_900, "NETFLIX", "Netflix")), TODAY)).isEmpty();
    }

    @Test
    @DisplayName("weekly payments are detected as weekly")
    void weekly() {
        List<Detected> found = RecurringDetector.detect(List.of(
                out("2026-09-01", 50_000, "MAID SALARY", null),
                out("2026-09-08", 50_000, "MAID SALARY", null),
                out("2026-09-15", 50_000, "MAID SALARY", null),
                out("2026-09-22", 50_000, "MAID SALARY", null)), TODAY);

        assertThat(found).extracting(Detected::frequency).containsExactly(Frequency.WEEKLY);
        assertThat(found.get(0).nextDue()).isEqualTo(LocalDate.of(2026, 9, 29));
    }

    @Test
    @DisplayName("transfers between your own accounts are ignored; salary is found as money coming in")
    void transfersIgnoredIncomeFound() {
        List<Detected> found = RecurringDetector.detect(List.of(
                point("2026-07-04", -200_000, "ATM WITHDRAWAL", null, "Transfer"),
                point("2026-08-04", -200_000, "ATM WITHDRAWAL", null, "Transfer"),
                point("2026-09-04", -200_000, "ATM WITHDRAWAL", null, "Transfer"),
                point("2026-07-01", 9_200_000, "SALARY", "Acme Tech", "Salary"),
                point("2026-08-01", 9_200_000, "SALARY", "Acme Tech", "Salary"),
                point("2026-09-01", 9_200_000, "SALARY", "Acme Tech", "Salary")), TODAY);

        assertThat(found).extracting(Detected::name).containsExactly("Acme Tech");
        assertThat(found.get(0).direction()).isEqualTo(Direction.IN);
    }

    @Test
    @DisplayName("on a year of demo data it finds exactly the real repeating payments and none of the noise")
    void demoData() {
        List<TxnPoint> points = DemoDataGenerator.generate(TODAY, 42).stream()
                .map(t -> new TxnPoint(t.date(), t.amountPaise(), t.description(), t.merchant(), null, t.category(), null))
                .toList();

        assertThat(RecurringDetector.detect(points, TODAY)).extracting(Detected::name).containsExactlyInAnyOrder(
                "Acme Tech", "R Kumar (landlord)", "Cult.fit", "BESCOM", "PPFAS Mutual Fund",
                "ACT Fibernet", "Jio", "Netflix", "Spotify", "HDFC Bank");
    }

    // ------------------------------------------------------------------ helpers

    private static TxnPoint out(String date, long amountPaise, String description, String merchant) {
        return point(date, -amountPaise, description, merchant, null);
    }

    private static TxnPoint point(String date, long amountPaise, String description, String merchant, String category) {
        return new TxnPoint(LocalDate.parse(date), amountPaise, description, merchant, null, category, null);
    }
}
