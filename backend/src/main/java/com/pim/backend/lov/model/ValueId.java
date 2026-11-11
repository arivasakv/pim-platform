package com.pim.backend.lov.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The ID of a value inside its list (FR-007, FR-008; research R-07): trimmed, 1–40 characters of {@code A–Z a–z 0–9
 * - _ .}, kept as text with its case and leading zeros ({@code 0001}). Two IDs that differ only in letter case are the
 * same ID (A-004). It never changes once saved (FR-010).
 */
public final class ValueId {

    /** Longest accepted ID. */
    public static final int MAX_LENGTH = 40;

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._-]{1,40}");
    private static final String FIELD = "id";

    private final String value;
    private final String key;

    private ValueId(String value) {
        this.value = value;
        this.key = keyOf(value);
    }

    /**
     * An ID typed by a user or read from a file.
     *
     * @throws InvalidListException {@code INVALID_VALUE_ID} when absent, blank, too long or with other characters
     */
    public static ValueId of(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (!ALLOWED.matcher(trimmed).matches()) {
            throw new InvalidListException(FIELD, LovErrorCode.INVALID_VALUE_ID);
        }
        return new ValueId(trimmed);
    }

    /** The comparison key of any text, without validating it: trimmed and upper-cased ({@code Locale.ROOT}). */
    public static String keyOf(String text) {
        return text.strip().toUpperCase(Locale.ROOT);
    }

    /** The ID as stored (trimmed, case kept). */
    public String value() {
        return value;
    }

    /** Key that is equal for IDs differing only in letter case. */
    public String key() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ValueId id && key.equals(id.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
