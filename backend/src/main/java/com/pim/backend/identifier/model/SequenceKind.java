package com.pim.backend.identifier.model;

import java.util.Optional;

/** Whether generated IDs are numbers only or a prefix plus a number (spec FR-006; plan decision D-2). */
public enum SequenceKind {
    NUMERIC,
    ALPHANUMERIC;

    /** The kind with exactly this code, or empty when the code is unknown. */
    public static Optional<SequenceKind> fromCode(String code) {
        for (SequenceKind kind : values()) {
            if (kind.name().equals(code)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
