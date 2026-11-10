package com.pim.backend.attributegroup.model;

import java.util.Locale;

/**
 * The name of an attribute group: trimmed, 1–80 characters (plan decision D-5). Two names that differ only in letter
 * case are the same name (FR-003); the value keeps the case given.
 */
public final class GroupName {

    /** Longest accepted name. */
    public static final int MAX_LENGTH = 80;

    private static final String FIELD = "name";

    private final String value;
    private final String comparisonKey;

    private GroupName(String value) {
        this.value = value;
        this.comparisonKey = value.toLowerCase(Locale.ROOT);
    }

    /**
     * The name typed by a user.
     *
     * @throws InvalidGroupException {@code REQUIRED} when absent; {@code INVALID_GROUP_NAME} when blank or longer than
     *     80 characters after trimming
     */
    public static GroupName of(String text) {
        if (text == null) {
            throw new InvalidGroupException(FIELD, AttributeGroupErrorCode.REQUIRED);
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            throw new InvalidGroupException(FIELD, AttributeGroupErrorCode.INVALID_GROUP_NAME);
        }
        return new GroupName(trimmed);
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
        return other instanceof GroupName name && comparisonKey.equals(name.comparisonKey);
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
