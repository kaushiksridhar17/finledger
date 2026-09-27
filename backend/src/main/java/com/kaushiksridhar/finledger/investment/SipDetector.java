package com.kaushiksridhar.finledger.investment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Spots mutual fund SIP debits in bank statements, e.g. "NACH/BSE STARMF/SIP PARAG PARIKH FLEXI CAP 000123",
 * and turns them into a stable key (so every month's debit groups together) and a guess at the fund's
 * name to search for ("Parag Parikh Flexi Cap").
 *
 * Plain Java with no Spring, so it's fast to unit test.
 */
public final class SipDetector {

    public static final int MAX_KEY_LENGTH = 120;

    // Words that show up in SIP debits: the SIP itself, and the exchanges and registrars that collect them
    private static final Pattern SIP_WORDS = Pattern.compile(
            "\\b(SIP|MUTUAL ?FUNDS?|STARMF|BSE ?STAR|MF ?UTILITY|MFUTILITY|NSE ?MFSS|CAMS|KFIN|KFINTECH)\\b");

    // Payment plumbing that says nothing about which fund it is
    private static final Set<String> JARGON = Set.of(
            "NACH", "ACH", "ECS", "DR", "CR", "UPI", "BSE", "NSE", "STARMF", "STAR", "MF", "MFSS", "SIP",
            "MUTUAL", "FUND", "FUNDS", "CAMS", "KFIN", "KFINTECH", "UTILITY", "MFUTILITY", "BILLDESK",
            "AUTOPAY", "MANDATE", "DEBIT", "TO", "FOR", "LTD", "PVT", "INDIA");

    private SipDetector() {
    }

    /** True for money going out that looks like a mutual fund investment. */
    public static boolean looksLikeSip(String description, String merchant, String categoryName) {
        if ("Investment".equals(categoryName)) {
            return true;
        }
        String text = (description + " " + (merchant == null ? "" : merchant)).toUpperCase(Locale.ROOT);
        return SIP_WORDS.matcher(text).find();
    }

    /**
     * Groups one SIP's monthly debits together: upper case, punctuation turned into spaces, and any word
     * containing a digit (reference numbers, dates) dropped.
     */
    public static String key(String description) {
        List<String> words = new ArrayList<>();
        for (String word : description.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+")) {
            if (!word.isEmpty() && word.chars().noneMatch(Character::isDigit)) {
                words.add(word);
            }
        }
        String key = String.join(" ", words);
        return key.length() <= MAX_KEY_LENGTH ? key : key.substring(0, MAX_KEY_LENGTH);
    }

    /** A guess at the fund's name, to prefill the search: the words after "SIP", without payment jargon. */
    public static String fundQuery(String description, String merchant) {
        List<String> words = List.of(key(description).split(" "));
        int sip = words.indexOf("SIP");
        List<String> candidates = sip >= 0 ? words.subList(sip + 1, words.size()) : words;

        List<String> useful = candidates.stream().filter(w -> w.length() > 1 && !JARGON.contains(w)).limit(5).toList();
        if (useful.isEmpty() && merchant != null) {
            useful = List.of(key(merchant).split(" ")).stream()
                    .filter(w -> w.length() > 1 && !JARGON.contains(w)).limit(5).toList();
        }
        return titleCase(String.join(" ", useful));
    }

    static String titleCase(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.toLowerCase(Locale.ROOT).split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
