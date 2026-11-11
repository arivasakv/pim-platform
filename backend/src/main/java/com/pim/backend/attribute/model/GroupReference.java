package com.pim.backend.attribute.model;

import java.util.Objects;

/**
 * An attribute group that contains an attribute, as feature 005 names it (feature 007 research R-02).
 *
 * @param id the group's identifier (feature 005 FR-016)
 * @param name the group's name
 */
public record GroupReference(String id, String name) {

    public GroupReference {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}
