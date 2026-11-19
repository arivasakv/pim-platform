package com.pim.backend.taxonomy.model;

import java.util.List;
import java.util.Objects;

/**
 * A list-of-values attribute that applies to a node, with how many of its values are allowed there (feature 012
 * FR-013; contract {@code ListAttribute}).
 *
 * @param selectionMode {@code SINGLE} or {@code MULTIPLE} (feature 004)
 * @param filters the filters on the node and its ancestors for this attribute, from the top level down
 */
public record ListAttributeSummary(
        String systemId,
        String displayName,
        String listOfValuesId,
        String listName,
        String selectionMode,
        int totalValues,
        int allowedCount,
        boolean hasOwnFilter,
        List<AllowedValues.FilterOnPath> filters) {

    public ListAttributeSummary {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(listOfValuesId, "listOfValuesId");
        Objects.requireNonNull(listName, "listName");
        Objects.requireNonNull(selectionMode, "selectionMode");
        filters = List.copyOf(filters);
    }
}
