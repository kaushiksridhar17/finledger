package com.kaushiksridhar.finledger.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.kaushiksridhar.finledger.category.CategoryKind;
import com.kaushiksridhar.finledger.rules.Categorizer.CategoryRef;
import com.kaushiksridhar.finledger.rules.Categorizer.Result;
import com.kaushiksridhar.finledger.rules.Categorizer.UserRule;

class CategorizerTest {

    private static final long FOOD = 1, GROCERIES = 2, SHOPPING = 3, SUBSCRIPTIONS = 4, TRANSPORT = 5,
            SALARY = 10, INTEREST = 11, REFUNDS = 12, CARD_PAYMENT = 20, PET_CARE = 30;

    private static final Map<String, CategoryRef> CATEGORIES = Map.of(
            "Food & Dining", new CategoryRef(FOOD, CategoryKind.EXPENSE),
            "Groceries", new CategoryRef(GROCERIES, CategoryKind.EXPENSE),
            "Shopping", new CategoryRef(SHOPPING, CategoryKind.EXPENSE),
            "Subscriptions", new CategoryRef(SUBSCRIPTIONS, CategoryKind.EXPENSE),
            "Transport", new CategoryRef(TRANSPORT, CategoryKind.EXPENSE),
            "Salary", new CategoryRef(SALARY, CategoryKind.INCOME),
            "Interest & Dividends", new CategoryRef(INTEREST, CategoryKind.INCOME),
            "Refunds", new CategoryRef(REFUNDS, CategoryKind.INCOME),
            "Credit Card Payment", new CategoryRef(CARD_PAYMENT, CategoryKind.TRANSFER));

    private final Categorizer builtInOnly = new Categorizer(List.of(), CATEGORIES);

    @Test
    @DisplayName("built-in rules pick the category and a tidy merchant name")
    void builtInMerchant() {
        assertThat(builtInOnly.categorize("UPI/SWIGGY/428112345674/Order", -43_200))
                .isEqualTo(new Result(FOOD, "Swiggy"));
        assertThat(builtInOnly.categorize("SALARY ACME TECH PVT LTD", 9_200_000))
                .isEqualTo(new Result(SALARY, null));
    }

    @Test
    @DisplayName("keywords match whole words only: OLA matches OLA CABS but not COCA COLA")
    void wholeWords() {
        assertThat(builtInOnly.categorize("UPI/OLA CABS/123", -24_600).categoryId()).isEqualTo(TRANSPORT);
        assertThat(builtInOnly.categorize("COCA COLA VENDING", -4_000).categoryId()).isNull();
    }

    @Test
    @DisplayName("more specific phrases win: AMAZON PRIME is a subscription, AMAZON alone is shopping")
    void specificBeforeGeneral() {
        assertThat(builtInOnly.categorize("AMAZON PRIME MEMBERSHIP", -149_900).categoryId()).isEqualTo(SUBSCRIPTIONS);
        assertThat(builtInOnly.categorize("UPI/AMAZON PAY/Order", -249_900).categoryId()).isEqualTo(SHOPPING);
        assertThat(builtInOnly.categorize("AMAZON REFUND 407", 89_900).categoryId()).isEqualTo(REFUNDS);
    }

    @Test
    @DisplayName("a built-in rule that points the wrong way is skipped")
    void directionMatters() {
        // Interest charged to you is not income
        assertThat(builtInOnly.categorize("INTEREST CHARGED ON OVERDUE", -50_000).categoryId()).isNull();
        // Money in from Swiggy (e.g. a payout) is not food spending
        assertThat(builtInOnly.categorize("SWIGGY PAYOUT", 150_000).categoryId()).isNull();
        // Transfers fit either way
        assertThat(builtInOnly.categorize("ICICI CREDIT CARD PAYMENT", -1_845_600).categoryId()).isEqualTo(CARD_PAYMENT);
    }

    @Test
    @DisplayName("the user's own rules come first, oldest first, and keep the built-in merchant name")
    void userRulesFirst() {
        Categorizer categorizer = new Categorizer(List.of(
                new UserRule(RuleMatchType.CONTAINS, "SWIGGY", PET_CARE),
                new UserRule(RuleMatchType.CONTAINS, "SWIGGY", GROCERIES)), CATEGORIES);

        assertThat(categorizer.categorize("UPI/Swiggy/Order", -43_200)).isEqualTo(new Result(PET_CARE, "Swiggy"));
    }

    @Test
    @DisplayName("STARTS_WITH only matches at the beginning; matching ignores case")
    void startsWith() {
        Categorizer categorizer = new Categorizer(List.of(
                new UserRule(RuleMatchType.STARTS_WITH, "UPI/CHAI", FOOD)), CATEGORIES);

        assertThat(categorizer.categorize("upi/chai point", -2_000).categoryId()).isEqualTo(FOOD);
        assertThat(categorizer.categorize("NEFT UPI/CHAI", -2_000).categoryId()).isNull();
    }

    @Test
    @DisplayName("nothing matches: uncategorised")
    void noMatch() {
        assertThat(builtInOnly.categorize("UPI/RAMESH GENERAL STORES", -35_000)).isEqualTo(new Result(null, null));
    }
}
