package com.kaushiksridhar.finledger.importing.format;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Shared parsing helpers for every bank layout: dates, amounts and descriptions. */
abstract class BaseFormat implements StatementFormat {

    static final int MAX_DESCRIPTION = 255;

    private final String name;
    private final List<DateTimeFormatter> dateFormats;

    protected BaseFormat(String name, String... datePatterns) {
        this.name = name;
        // STRICT rejects impossible dates like 31/02/2026 instead of quietly moving them to 3 March
        this.dateFormats = Arrays.stream(datePatterns)
                .map(pattern -> new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern(pattern)
                        .toFormatter(Locale.ENGLISH)
                        .withResolverStyle(ResolverStyle.STRICT))
                .toList();
    }

    @Override
    public String name() {
        return name;
    }

    protected LocalDate parseDate(String text) {
        String cleaned = text.trim();
        for (DateTimeFormatter format : dateFormats) {
            try {
                return LocalDate.parse(cleaned, format);
            } catch (DateTimeParseException ignored) {
                // try the next pattern
            }
        }
        throw new RowException("Couldn't read the date \"" + cleaned + "\"");
    }

    /**
     * "1,20,000.50" -> 12000050 paise. Blank or "-" means no amount (null).
     * "(450.00)" is read as -450.00, the way some spreadsheets show negatives.
     * Goes through BigDecimal, never double, so no paise are ever lost to rounding.
     */
    protected static Long parseAmount(String text) {
        String cleaned = text.replace(",", "").replace("\u20B9", "").replaceAll("\\s+", "");
        if (cleaned.isEmpty() || cleaned.equals("-")) {
            return null;
        }

        boolean bracketed = cleaned.startsWith("(") && cleaned.endsWith(")");
        if (bracketed) {
            cleaned = "-" + cleaned.substring(1, cleaned.length() - 1);
        }

        BigDecimal value;
        try {
            value = new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new RowException("Couldn't read the amount \"" + text.trim() + "\"");
        }

        if (value.stripTrailingZeros().scale() > 2) {
            throw new RowException("The amount \"" + text.trim() + "\" has more than 2 decimal places");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    protected static String cleanDescription(String text) {
        String cleaned = text.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            throw new RowException("No description");
        }
        return cleaned.length() <= MAX_DESCRIPTION ? cleaned : cleaned.substring(0, MAX_DESCRIPTION);
    }

    protected static int column(List<String> header, String name) {
        int index = header.indexOf(name);
        if (index < 0) {
            throw new StatementFormatException("The file is missing the \"" + name + "\" column");
        }
        return index;
    }
}
