package com.pim.backend.taxonomy.model;

import java.util.Objects;

/**
 * A list attribute's values at a node: what the node's own filter may include, what it includes and what is allowed
 * (feature 012 FR-008 to FR-014; contract {@code ValueFilterDetail}).
 */
public record ValueFilterDetails(NodeReference node, ListAttributeSummary attribute, AllowedValues values) {

    public ValueFilterDetails {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(values, "values");
    }
}
