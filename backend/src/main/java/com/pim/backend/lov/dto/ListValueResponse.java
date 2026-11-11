package com.pim.backend.lov.dto;

import com.pim.backend.lov.model.ListValue;

/** One value (contract {@code ListValue}); {@code equivalence} is {@code null} when there is none. */
public record ListValueResponse(String id, String label, String equivalence) {

    public static ListValueResponse from(ListValue value) {
        return new ListValueResponse(
                value.id().value(), value.label(), value.equivalence().orElse(null));
    }
}
