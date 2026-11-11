package com.pim.backend.attribute.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An attribute with what other features know about it, for the attribute list and detail (feature 007 FR-004,
 * FR-006; research R-07).
 *
 * @param attribute the attribute definition
 * @param groups the groups containing it, sorted by name ignoring letter case; empty when none
 * @param listOfValuesName the linked list's name for a list-of-values attribute; empty otherwise or if the list is
 *     not found
 */
public record AttributeDetails(Attribute attribute, List<GroupReference> groups, Optional<String> listOfValuesName) {

    public AttributeDetails {
        Objects.requireNonNull(attribute, "attribute");
        groups = List.copyOf(groups);
        Objects.requireNonNull(listOfValuesName, "listOfValuesName");
    }
}
