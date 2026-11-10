package com.pim.backend.attribute.model;

import java.util.Optional;

/** The seven data types of ERF 1.1 §3.2.1, in the order of the source (FR-007). */
public enum AttributeDataType {
    TEXT("Texto"),
    NUMERIC("Numérico"),
    BOOLEAN("Booleano"),
    LIST_OF_VALUES("Lista de valores"),
    DATE_TIME("Fecha y hora"),
    CURRENCY("Moneda"),
    REFERENCE("Referencia");

    private final String label;

    AttributeDataType(String label) {
        this.label = label;
    }

    /** Spanish label shown to users. */
    public String label() {
        return label;
    }

    /** The type with exactly this code, or empty when the code is unknown. */
    public static Optional<AttributeDataType> fromCode(String code) {
        return Codes.find(values(), code);
    }
}
