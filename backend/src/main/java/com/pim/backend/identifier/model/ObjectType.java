package com.pim.backend.identifier.model;

import java.util.Optional;

/** The six object types that receive a system ID (ERF 1.1 §3.1; spec FR-001), in the order of the source. */
public enum ObjectType {
    PRODUCT,
    ATTRIBUTE,
    LIST_OF_VALUES,
    TEMPLATE,
    CLASSIFICATION,
    INTERFACE;

    /** The type with exactly this code, or empty when the code is unknown. */
    public static Optional<ObjectType> fromCode(String code) {
        for (ObjectType type : values()) {
            if (type.name().equals(code)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
