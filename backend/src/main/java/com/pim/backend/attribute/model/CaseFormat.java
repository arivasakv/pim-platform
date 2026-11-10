package com.pim.backend.attribute.model;

import java.util.Optional;

/** Mask that forces the letter case of a text attribute (ERF 1.1 §3.2.1 Texto; FR-008, plan decision D-6). */
public enum CaseFormat {
    UPPERCASE("Mayúsculas"),
    LOWERCASE("Minúsculas"),
    MIXED_CASE("Capitalización mixta");

    private final String label;

    CaseFormat(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<CaseFormat> fromCode(String code) {
        return Codes.find(values(), code);
    }
}
