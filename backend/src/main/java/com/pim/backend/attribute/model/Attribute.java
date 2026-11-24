package com.pim.backend.attribute.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An attribute definition (FR-003, FR-007, FR-018). Always global (FR-018). Feature 007 lets its display name and
 * properties change ({@link #updated}); its system ID, data type, scope and creation date never do (feature 007
 * FR-010). Group links live in the attribute groups (feature 005). Attributes have no description since the owner's
 * amendment of 2026-11-20 (FR-003).
 *
 * @param systemId system ID given by feature 003 (FR-004)
 * @param displayName unique display name (FR-023)
 * @param dataType one of the seven data types
 * @param scope always {@link AttributeScope#GLOBAL}
 * @param properties the properties of {@code dataType}; {@code null} for {@code BOOLEAN}
 * @param createdAt when the attribute was created
 * @param updatedAt when it was last changed; empty until the first edit (feature 007 research R-08)
 */
public record Attribute(
        String systemId,
        DisplayName displayName,
        AttributeDataType dataType,
        AttributeScope scope,
        AttributeProperties properties,
        Instant createdAt,
        Optional<Instant> updatedAt) {

    public Attribute {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(dataType, "dataType");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        boolean valid = dataType == AttributeDataType.BOOLEAN
                ? properties == null
                : properties != null && properties.dataType() == dataType;
        if (!valid) {
            throw new IllegalArgumentException("Properties do not match the data type " + dataType);
        }
    }

    /** A new global attribute. */
    public static Attribute create(
            String systemId,
            DisplayName displayName,
            AttributeDataType dataType,
            AttributeProperties properties,
            Instant createdAt) {
        return new Attribute(
                systemId, displayName, dataType, AttributeScope.GLOBAL, properties, createdAt, Optional.empty());
    }

    /**
     * This attribute with a new display name and properties, changed at {@code at}; the system ID, data type, scope
     * and creation date stay (feature 007 FR-008, FR-010).
     *
     * @throws IllegalArgumentException if the properties do not match the data type
     */
    public Attribute updated(DisplayName newDisplayName, AttributeProperties newProperties, Instant at) {
        return new Attribute(systemId, newDisplayName, dataType, scope, newProperties, createdAt, Optional.of(at));
    }

    /**
     * This attribute with another data type and its properties, changed at {@code at}: only for unlinking a list of
     * values (feature 014 FR-001, FR-013; research R-05). The system ID, display name, scope and creation date stay.
     *
     * @throws IllegalArgumentException if the properties do not match the new data type
     */
    public Attribute withDataType(AttributeDataType newDataType, AttributeProperties newProperties, Instant at) {
        return new Attribute(systemId, displayName, newDataType, scope, newProperties, createdAt, Optional.of(at));
    }
}
