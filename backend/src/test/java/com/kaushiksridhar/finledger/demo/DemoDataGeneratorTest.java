package com.kaushiksridhar.finledger.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kaushiksridhar.finledger.demo.DemoDataGenerator.AccountKey;
import com.kaushiksridhar.finledger.demo.DemoDataGenerator.DemoTransaction;

/** Plain unit tests of the demo data. No Spring, no database. */
class DemoDataGeneratorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    // Must match the built-in categories inserted by the V3 migration
    private static final Set<String> BUILT_IN_CATEGORIES = Set.of(
            "Food & Dining", "Groceries", "Rent", "Bills & Utilities", "Transport", "Shopping",
            "Entertainment", "Subscriptions", "Health", "Education", "Travel", "Personal Care",
            "Gifts & Donations", "Other Expense", "Salary", "Freelance", "Interest & Dividends",
            "Refunds", "Other Income", "Transfer", "Investment", "Credit Card Payment");

    private final List<DemoTransaction> transactions = DemoDataGenerator.generate(TODAY, 42);

    @Test
    @DisplayName("the same date and seed always produce exactly the same data")
    void deterministic() {
        assertThat(DemoDataGenerator.generate(TODAY, 42)).isEqualTo(transactions);
        assertThat(DemoDataGenerator.generate(TODAY, 7)).isNotEqualTo(transactions);
    }

    @Test
    @DisplayName("covers the 12 months up to today and nothing in the future")
    void dateRange() {
        LocalDate firstDay = YearMonth.from(TODAY).minusMonths(11).atDay(1);

        assertThat(transactions).allSatisfy(t -> assertThat(t.date()).isBetween(firstDay, TODAY));
        assertThat(transactions).hasSizeBetween(600, 900);
    }

    @Test
    @DisplayName("salary arrives on the 1st of every one of the 12 months")
    void monthlySalary() {
        List<DemoTransaction> salaries = transactions.stream()
                .filter(t -> t.category().equals("Salary"))
                .toList();

        assertThat(salaries).hasSize(12);
        assertThat(salaries).allSatisfy(t -> {
            assertThat(t.date().getDayOfMonth()).isEqualTo(1);
            assertThat(t.amountPaise()).isPositive();
        });
    }

    @Test
    @DisplayName("every amount is non-zero and every category is a real built-in one")
    void validRows() {
        assertThat(transactions).allSatisfy(t -> {
            assertThat(t.amountPaise()).isNotZero();
            assertThat(BUILT_IN_CATEGORIES).contains(t.category());
            assertThat(t.description()).isNotBlank();
        });
    }

    @Test
    @DisplayName("transfers between Arjun's own accounts always net to zero")
    void transfersBalance() {
        long net = transactions.stream()
                .filter(t -> t.category().equals("Transfer"))
                .mapToLong(DemoTransaction::amountPaise)
                .sum();

        assertThat(net).isZero();
    }

    @Test
    @DisplayName("each credit card bill pays exactly the previous month's card spending")
    void cardBillMatchesLastMonth() {
        Map<YearMonth, Long> cardNetByMonth = new HashMap<>();
        for (DemoTransaction t : transactions) {
            if (t.account() == AccountKey.CARD && !t.category().equals(DemoDataGenerator.CARD_PAYMENT)) {
                cardNetByMonth.merge(YearMonth.from(t.date()), t.amountPaise(), Long::sum);
            }
        }

        List<DemoTransaction> billPayments = transactions.stream()
                .filter(t -> t.account() == AccountKey.HDFC && t.category().equals(DemoDataGenerator.CARD_PAYMENT))
                .toList();

        assertThat(billPayments).isNotEmpty();
        assertThat(billPayments).allSatisfy(payment -> {
            YearMonth previous = YearMonth.from(payment.date()).minusMonths(1);
            assertThat(payment.amountPaise()).isEqualTo(cardNetByMonth.get(previous));
        });
    }
}
