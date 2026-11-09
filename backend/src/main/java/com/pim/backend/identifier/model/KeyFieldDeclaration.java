package com.pim.backend.identifier.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The key fields of an object type: the fields compared to decide whether an incoming object with an existing ID
 * is the same object (FR-009a, FR-013). This feature fixes no key fields; every type starts with an empty
 * declaration (plan decision D-8).
 *
 * @param fieldNames 0–20 distinct names, each 1–60 letters, digits or underscores, starting with a letter
 */
public record KeyFieldDeclaration(List<String> fieldNames) {

    private static final int MAX_FIELDS = 20;
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,59}");

    public KeyFieldDeclaration {
        if (fieldNames == null || fieldNames.size() > MAX_FIELDS) {
            throw invalid();
        }
        Set<String> seen = new HashSet<>();
        for (String name : fieldNames) {
            if (name == null || !NAME.matcher(name).matches() || !seen.add(name)) {
                throw invalid();
            }
        }
        fieldNames = List.copyOf(fieldNames);
    }

    /** A declaration with no key fields. */
    public static KeyFieldDeclaration empty() {
        return new KeyFieldDeclaration(List.of());
    }

    /** Whether no key field is declared. */
    public boolean isEmpty() {
        return fieldNames.isEmpty();
    }

    private static InvalidIdentifierRequestException invalid() {
        return new InvalidIdentifierRequestException("keyFields", IdentifierErrorCode.INVALID_KEY_FIELD_NAME);
    }
}
