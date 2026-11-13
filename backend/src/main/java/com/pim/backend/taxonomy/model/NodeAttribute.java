package com.pim.backend.taxonomy.model;

import java.util.Objects;

/**
 * An effective attribute with the attribute's current name and data type (contract {@code EffectiveAttribute}).
 *
 * @param effective assignment type and origin
 * @param systemId the attribute's own system ID
 * @param displayName the attribute's current display name
 * @param dataType the attribute's data type code (feature 004)
 */
public record NodeAttribute(EffectiveAttribute effective, String systemId, String displayName, String dataType) {

    public NodeAttribute {
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(dataType, "dataType");
    }
}
