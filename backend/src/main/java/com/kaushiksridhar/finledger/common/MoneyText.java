package com.kaushiksridhar.finledger.common;

/**
 * Formats paise as Indian rupees for messages: 510000 -> "Rs 5,100", 12345650 -> "Rs 1,23,456.50"
 * (with the rupee sign). Java's own number formats don't do Indian lakh/crore grouping, so it's done by hand.
 */
public final class MoneyText {

    private static final String RUPEE = "\u20B9";

    private MoneyText() {
    }

    public static String format(long paise) {
        long abs = Math.abs(paise);
        String rupees = groupIndian(Long.toString(abs / 100));
        long rest = abs % 100;
        String text = RUPEE + rupees + (rest == 0 ? "" : "." + (rest < 10 ? "0" : "") + rest);
        return paise < 0 ? "-" + text : text;
    }

    /** "12345678" -> "1,23,45,678": the last three digits, then groups of two. */
    static String groupIndian(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        String lastThree = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);

        StringBuilder grouped = new StringBuilder();
        while (rest.length() > 2) {
            grouped.insert(0, "," + rest.substring(rest.length() - 2));
            rest = rest.substring(0, rest.length() - 2);
        }
        grouped.insert(0, rest);
        return grouped + "," + lastThree;
    }
}
