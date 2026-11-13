package com.pim.backend.taxonomy.model;

import java.util.Objects;

/**
 * A node named in a path, a list of children or an origin (contract {@code NodeReference}).
 *
 * @param systemId the node's system ID
 * @param name the node's current name
 */
public record NodeReference(String systemId, String name) {

    public NodeReference {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(name, "name");
    }
}
