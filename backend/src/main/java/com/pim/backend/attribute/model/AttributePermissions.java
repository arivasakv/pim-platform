package com.pim.backend.attribute.model;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The requesting user's permission codes (FR-002, FR-019). {@code ATTRIBUTE_CREATE} allows creating attributes;
 * while every attribute is global it also covers affecting the global structure (plan decision D-2).
 *
 * @param codes permission codes, upper-cased
 */
public record AttributePermissions(Set<String> codes) {

    /** Code that allows creating attributes. */
    public static final String ATTRIBUTE_CREATE = "ATTRIBUTE_CREATE";

    public AttributePermissions {
        codes = Set.copyOf(codes);
    }

    /** Permissions from a comma-separated list of codes; codes are trimmed and compared ignoring letter case. */
    public static AttributePermissions fromCodes(String commaSeparatedCodes) {
        if (commaSeparatedCodes == null) {
            return new AttributePermissions(Set.of());
        }
        Set<String> codes = java.util.Arrays.stream(commaSeparatedCodes.split(","))
                .map(code -> code.strip().toUpperCase(Locale.ROOT))
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toSet());
        return new AttributePermissions(codes);
    }

    /** Whether the user may create attributes. */
    public boolean allowsCreation() {
        return codes.contains(ATTRIBUTE_CREATE);
    }
}
