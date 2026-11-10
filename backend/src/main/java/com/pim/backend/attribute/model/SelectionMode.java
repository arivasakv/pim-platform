package com.pim.backend.attribute.model;

import java.util.Optional;

/** Single or multiple selection of a list-of-values attribute (ERF 1.1 §3.2.1 Listas de Valores; FR-012). */
public enum SelectionMode {
    SINGLE("Selección única"),
    MULTIPLE("Selección múltiple");

    private final String label;

    SelectionMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<SelectionMode> fromCode(String code) {
        return Codes.find(values(), code);
    }
}
