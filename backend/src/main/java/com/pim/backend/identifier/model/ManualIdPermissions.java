package com.pim.backend.identifier.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * The object types for which the requesting user may give manual IDs (FR-003: one permission per type).
 *
 * @param objectTypes the permitted types
 */
public record ManualIdPermissions(Set<ObjectType> objectTypes) {

    public ManualIdPermissions {
        objectTypes = objectTypes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(objectTypes));
    }

    /**
     * Permissions from a comma-separated list of object-type codes, such as {@code "PRODUCT,TEMPLATE"}. Codes are
     * trimmed and compared ignoring letter case; unknown codes are ignored; {@code null} or blank gives none.
     */
    public static ManualIdPermissions fromCodes(String codes) {
        Set<ObjectType> types = EnumSet.noneOf(ObjectType.class);
        if (codes != null) {
            for (String code : codes.split(",")) {
                ObjectType.fromCode(code.strip().toUpperCase(Locale.ROOT)).ifPresent(types::add);
            }
        }
        return new ManualIdPermissions(types);
    }

    /** Whether manual IDs may be given for objects of this type. */
    public boolean allows(ObjectType objectType) {
        return objectTypes.contains(objectType);
    }
}
