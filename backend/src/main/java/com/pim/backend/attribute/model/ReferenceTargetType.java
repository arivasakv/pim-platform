package com.pim.backend.attribute.model;

import java.util.Optional;

/**
 * Kind of system object a reference attribute links a product to (FR-015, plan decision D-11). The codes are
 * those of feature 003's object types; a test keeps the two lists equal.
 */
public enum ReferenceTargetType {
    PRODUCT("Producto"),
    ATTRIBUTE("Atributo"),
    LIST_OF_VALUES("Lista de Valores (LOV's)"),
    TEMPLATE("Plantilla"),
    CLASSIFICATION("Clasificación"),
    INTERFACE("Interfaz");

    private final String label;

    ReferenceTargetType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<ReferenceTargetType> fromCode(String code) {
        return Codes.find(values(), code);
    }
}
