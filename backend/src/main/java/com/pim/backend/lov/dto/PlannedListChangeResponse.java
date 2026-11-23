package com.pim.backend.lov.dto;

import com.pim.backend.lov.model.PlannedListChange;

/**
 * One list a bulk load creates or updates (feature 013 contract {@code PlannedListChange}); {@code systemId} is
 * {@code null} in a summary for a list still to create.
 */
public record PlannedListChangeResponse(
        String name, String systemId, String action, int valuesAdded, int valuesUpdated) {

    public static PlannedListChangeResponse from(PlannedListChange change) {
        return new PlannedListChangeResponse(
                change.name(),
                change.systemId().orElse(null),
                change.action().name(),
                change.valuesAdded(),
                change.valuesUpdated());
    }
}
