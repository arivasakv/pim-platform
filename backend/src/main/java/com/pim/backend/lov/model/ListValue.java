package com.pim.backend.lov.model;

import java.util.Optional;

/**
 * One value of a list: its ID, label and optional equivalence (FR-006, FR-023; data-model "ListValue"). Labels and
 * equivalences may repeat within a list (A-005).
 */
public record ListValue(ValueId id, String label, Optional<String> equivalence) {

    /** Longest accepted label. */
    public static final int MAX_LABEL_LENGTH = 200;

    /** Longest accepted equivalence. */
    public static final int MAX_EQUIVALENCE_LENGTH = 200;

    /**
     * The label typed by a user: trimmed, 1–200 characters.
     *
     * @throws InvalidListException {@code REQUIRED} when absent; {@code INVALID_LABEL} when blank or too long
     */
    public static String label(String text) {
        if (text == null) {
            throw new InvalidListException("label", LovErrorCode.REQUIRED);
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LABEL_LENGTH) {
            throw new InvalidListException("label", LovErrorCode.INVALID_LABEL);
        }
        return trimmed;
    }

    /**
     * The equivalence typed by a user: trimmed, up to 200 characters; absent or blank means none.
     *
     * @throws InvalidListException {@code INVALID_EQUIVALENCE} when too long
     */
    public static Optional<String> equivalence(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String trimmed = text.strip();
        if (trimmed.length() > MAX_EQUIVALENCE_LENGTH) {
            throw new InvalidListException("equivalence", LovErrorCode.INVALID_EQUIVALENCE);
        }
        return Optional.of(trimmed);
    }
}
