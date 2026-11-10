package com.pim.backend.attribute.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An attribute definition (FR-003, FR-007, FR-018). Created once and never changed in this feature; always global
 * and without groups (FR-017, FR-018).
 *
 * @param systemId system ID given by feature 003 (FR-004)
 * @param displayName unique display name (FR-023)
 * @param description optional description
 * @param dataType one of the seven data types
 * @param scope always {@link AttributeScope#GLOBAL}
 * @param properties the properties of {@code dataType}; {@code null} for {@code BOOLEAN}
 * @param createdAt when the attribute was created
 */
public record Attribute(
        String systemId,
        DisplayName displayName,
        Optional<String> description,
        AttributeDataType dataType,
        AttributeScope scope,
        AttributeProperties properties,
        Instant createdAt) {

    /** Longest accepted description. */
    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    public Attribute {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(dataType, "dataType");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(createdAt, "createdAt");
        boolean valid = dataType == AttributeDataType.BOOLEAN
                ? properties == null
                : properties != null && properties.dataType() == dataType;
        if (!valid) {
            throw new IllegalArgumentException("Properties do not match the data type " + dataType);
        }
    }

    /**
     * A new global attribute.
     *
     * @throws InvalidAttributeException {@code INVALID_DESCRIPTION} for a description longer than 1,000 characters
     */
    public static Attribute create(
            String systemId,
            DisplayName displayName,
            String description,
            AttributeDataType dataType,
            AttributeProperties properties,
            Instant createdAt) {
        return new Attribute(
                systemId,
                displayName,
                normalizedDescription(description),
                dataType,
                AttributeScope.GLOBAL,
                properties,
                createdAt);
    }

    /**
     * The description trimmed; blank means absent.
     *
     * @throws InvalidAttributeException {@code INVALID_DESCRIPTION} when longer than 1,000 characters
     */
    public static Optional<String> normalizedDescription(String description) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        String trimmed = description.strip();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidAttributeException("description", AttributeErrorCode.INVALID_DESCRIPTION);
        }
        return Optional.of(trimmed);
    }
}
