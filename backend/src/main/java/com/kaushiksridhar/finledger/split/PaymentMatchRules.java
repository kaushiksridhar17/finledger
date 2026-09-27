package com.kaushiksridhar.finledger.split;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether a bank credit looks like a particular friend paying the user back.
 * Both have to be true:
 *  - the amount is what the settle-up plan says they owe you, give or take Rs 1
 *    (people round, and some apps add or take a rupee)
 *  - the description or merchant mentions them: their first name as a whole word,
 *    or their UPI ID. Indian UPI credits look like "UPI/CR/412345678901/ROHAN KULKARNI/okaxis/Goa".
 *
 * Plain Java with no Spring, so it's fast to unit test.
 */
public final class PaymentMatchRules {

    public static final long AMOUNT_TOLERANCE_PAISE = 100;

    private static final int MIN_NAME_LENGTH = 3;
    private static final Pattern NOT_LETTERS = Pattern.compile("[^\\p{L}]+");

    private PaymentMatchRules() {
    }

    public static boolean amountMatches(long receivedPaise, long expectedPaise) {
        return expectedPaise > 0 && Math.abs(receivedPaise - expectedPaise) <= AMOUNT_TOLERANCE_PAISE;
    }

    /** True if the text names this person. Short names like "Al" are too likely to appear by chance, so they never match. */
    public static boolean mentions(String text, String memberName, String upiId) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);

        if (upiId != null && !upiId.isBlank() && lower.contains(upiId.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }

        String first = firstName(memberName);
        if (first == null) {
            return false;
        }
        // Whole word only: "Ravi" must not match "RAVINDRA" or "GRAVITY"
        Pattern word = Pattern.compile("(?<!\\p{L})" + Pattern.quote(first) + "(?!\\p{L})",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return word.matcher(text).find();
    }

    /** The first part of a name that is at least 3 letters long: "Dr. Rao" -> "Rao", "Rohan K" -> "Rohan". */
    static String firstName(String name) {
        if (name == null) {
            return null;
        }
        for (String token : NOT_LETTERS.split(name.trim())) {
            if (token.length() >= MIN_NAME_LENGTH) {
                return token;
            }
        }
        return null;
    }
}
