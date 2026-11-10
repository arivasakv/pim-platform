package com.pim.backend.attribute.model;

import java.util.Locale;

/**
 * The display name of an attribute (FR-003): trimmed, 1–120 characters (plan decision D-7). Two names that differ
 * only in letter case are the same name (FR-023); the value keeps the case given.
 */
public final class DisplayName {

    /** Longest accepted display name. */
    public static final int MAX_LENGTH = 120;

    private static final String FIELD = "displayName";

    private final String value;
    private final String comparisonKey;

    private DisplayName(String value) {
        this.value = value;
        this.comparisonKey = value.toLowerCase(Locale.ROOT);
    }

    /**
     * The display name typed by a user.
     *
     * @throws InvalidAttributeException {@code REQUIRED} when absent; {@code INVALID_DISPLAY_NAME} when blank or
     *     longer than 120 characters after trimming
     */
    public static DisplayName of(String text) {
        if (text == null) {
            throw new InvalidAttributeException(FIELD, AttributeErrorCode.REQUIRED);
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            throw new InvalidAttributeException(FIELD, AttributeErrorCode.INVALID_DISPLAY_NAME);
        }
        return new DisplayName(trimmed);
    }

    /** The name as stored (trimmed, case kept). */
    public String value() {
        return value;
    }

    /** Key that is equal for names differing only in letter case. */
    public String comparisonKey() {
        return comparisonKey;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DisplayName name && comparisonKey.equals(name.comparisonKey);
    }

    @Override
    public int hashCode() {
        return comparisonKey.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
