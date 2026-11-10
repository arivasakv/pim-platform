package com.pim.backend.attribute.model;

import java.util.Optional;

/** Integer or decimal numbers (ERF 1.1 §3.2.1 Numéricos; FR-010, plan decision D-8). */
public enum NumberFormat {
    INTEGER("Entero"),
    DECIMAL("Decimal");

    private final String label;

    NumberFormat(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<NumberFormat> fromCode(String code) {
        return Codes.find(values(), code);
    }
}
