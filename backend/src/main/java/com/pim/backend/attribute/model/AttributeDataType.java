package com.pim.backend.attribute.model;

import java.util.Optional;

/**
 * The seven data types of ERF 1.1 §3.2.1, in the order of the source (FR-007). Labels follow the owner's amendment of
 * 2026-11-20 (FR-007): the user interface shows {@link #NUMERIC} as two choices, «Numérico Entero (Integer)» and
 * «Numérico Decimal (Float)», from its {@link NumberFormat}; its own label is used where the format is not known.
 */
public enum AttributeDataType {
    TEXT("Texto (String)"),
    NUMERIC("Numérico (Integer / Float)"),
    BOOLEAN("Booleano (Sí/No)"),
    LIST_OF_VALUES("Lista de Valores (LOV)"),
    DATE_TIME("Fecha y Hora (Date/Time)"),
    CURRENCY("Moneda (Currency)"),
    REFERENCE("Referencias / Relaciones");

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
