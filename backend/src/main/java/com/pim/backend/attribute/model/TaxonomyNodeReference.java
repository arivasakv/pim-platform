package com.pim.backend.attribute.model;

import java.util.Objects;

/**
 * A taxonomy node that lists an attribute directly, as feature 010 names it (feature 010 research R-05).
 *
 * @param systemId the node's system ID
 * @param name the node's name
 */
public record TaxonomyNodeReference(String systemId, String name) {

    public TaxonomyNodeReference {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(name, "name");
    }
}
