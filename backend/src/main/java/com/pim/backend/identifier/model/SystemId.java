package com.pim.backend.identifier.model;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The system ID of one object (FR-001). Its value is kept as given, but two IDs that differ only in letter case
 * are the same ID (research R-07, plan decision D-6). A system ID is never changed (FR-007).
 */
public final class SystemId {

    /** Longest accepted ID. */
    public static final int MAX_LENGTH = 40;

    private static final String FIELD = "systemId";
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9._-]{1," + MAX_LENGTH + "}");

    private final String value;
    private final String comparisonKey;

    private SystemId(String value) {
        this.value = value;
        this.comparisonKey = value.toUpperCase(Locale.ROOT);
    }

    /**
     * A manual ID typed by a user: surrounding spaces are removed; a missing or blank value means "no ID given"
     * (spec A-004) and returns empty.
     *
     * @throws InvalidIdentifierRequestException if the trimmed value is not 1–40 characters from
     *     {@code A–Z a–z 0–9 - _ .}
     */
    public static Optional<SystemId> parseManual(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(of(text.strip()));
    }

    /**
     * An ID with an already trimmed value, such as a generated one.
     *
     * @throws InvalidIdentifierRequestException if the value is not 1–40 characters from {@code A–Z a–z 0–9 - _ .}
     */
    public static SystemId of(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidIdentifierRequestException(FIELD, IdentifierErrorCode.INVALID_SYSTEM_ID);
        }
        return new SystemId(value);
    }

    /** The ID as it was given. */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SystemId systemId && comparisonKey.equals(systemId.comparisonKey);
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
