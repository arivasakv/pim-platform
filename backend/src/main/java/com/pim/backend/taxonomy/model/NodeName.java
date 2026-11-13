package com.pim.backend.taxonomy.model;

import java.util.Locale;

/**
 * The name of a taxonomy node: trimmed, 1–80 characters (spec A-006). Two names that differ only in letter case or
 * surrounding spaces are the same name among siblings (FR-004); the value keeps the case given.
 */
public final class NodeName {

    /** Longest accepted name. */
    public static final int MAX_LENGTH = 80;

    private static final String FIELD = "name";

    private final String value;
    private final String comparisonKey;

    private NodeName(String value) {
        this.value = value;
        this.comparisonKey = value.toLowerCase(Locale.ROOT);
    }

    /**
     * The name typed by a user.
     *
     * @throws InvalidNodeException {@code REQUIRED} when absent; {@code INVALID_NODE_NAME} when blank or longer than
     *     80 characters after trimming
     */
    public static NodeName of(String text) {
        if (text == null) {
            throw new InvalidNodeException(FIELD, TaxonomyErrorCode.REQUIRED);
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            throw new InvalidNodeException(FIELD, TaxonomyErrorCode.INVALID_NODE_NAME);
        }
        return new NodeName(trimmed);
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
        return other instanceof NodeName name && comparisonKey.equals(name.comparisonKey);
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
