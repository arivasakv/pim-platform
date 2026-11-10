package com.pim.backend.attribute.model;

/**
 * What a user asked to create, before the creation rules run (data-model "Creation flow").
 *
 * @param requestedSystemId manual system ID, or {@code null}/blank to have feature 003 generate one
 * @param displayName display name as typed
 * @param description description as typed, may be {@code null}
 * @param dataType the chosen data type
 * @param properties the properties of {@code dataType}, already checked for format; {@code null} for
 *     {@code BOOLEAN}
 */
public record AttributeDraft(
        String requestedSystemId,
        String displayName,
        String description,
        AttributeDataType dataType,
        AttributeProperties properties) {}
