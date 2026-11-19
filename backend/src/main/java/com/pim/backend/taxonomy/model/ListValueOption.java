package com.pim.backend.taxonomy.model;

import java.util.Objects;

/**
 * One value of a list as the taxonomy domain sees it (feature 012): its ID as stored in the list and its label.
 * Keeps the taxonomy model independent of the lov domain's types.
 */
public record ListValueOption(String id, String label) {

    public ListValueOption {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
    }

    /** The comparison key of the ID. */
    public String key() {
        return ValueFilter.valueKey(id);
    }
}
