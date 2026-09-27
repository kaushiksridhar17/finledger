package com.kaushiksridhar.finledger.importing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.kaushiksridhar.finledger.importing.format.StatementRow;

/**
 * Fingerprints for imported rows, so uploading the same statement twice (or two statements
 * that overlap by a few weeks) never creates duplicate transactions.
 *
 * fingerprint = SHA-256(account | date | amount | description | occurrence)
 *
 * The occurrence number matters: two genuinely separate Rs 20 chai payments on the same day
 * with the same description are both real. Within one file the first gets occurrence 1 and the
 * second occurrence 2, so both are imported. Upload the file again and they produce the same
 * two fingerprints, so both are recognised as already imported.
 */
public final class DedupeHashes {

    private DedupeHashes() {
    }

    /** One fingerprint per row, in the same order as the rows. */
    public static List<String> compute(long accountId, List<StatementRow> rows) {
        Map<String, Integer> seen = new HashMap<>();
        List<String> hashes = new ArrayList<>(rows.size());

        for (StatementRow row : rows) {
            String key = accountId + "|" + row.date() + "|" + row.amountPaise() + "|" + normalise(row.description());
            int occurrence = seen.merge(key, 1, Integer::sum);
            hashes.add(sha256(key + "|" + occurrence));
        }
        return hashes;
    }

    /** Case and spacing differences between exports shouldn't make the same row look new. */
    static String normalise(String description) {
        return description.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
