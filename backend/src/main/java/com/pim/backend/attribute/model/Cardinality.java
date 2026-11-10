package com.pim.backend.attribute.model;

import java.util.Optional;

/** Whether a reference attribute links one object or several (FR-015, plan decision D-11). */
public enum Cardinality {
    SINGLE("Una"),
    MULTIPLE("Varias");

    private final String label;

    Cardinality(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<Cardinality> fromCode(String code) {
        return Codes.find(values(), code);
    }
}
