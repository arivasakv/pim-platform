package com.pim.backend.lov.model;

import java.time.Instant;
import java.util.List;

/**
 * Who changed a list, when, and what (FR-019; research R-11; data-model "ChangeRecord").
 *
 * @param user from the development header {@code X-Pim-User}; {@code null} when absent
 * @param valueIds the deleted value ID, or empty
 * @param added values added by an import, otherwise 0
 * @param updated values updated by an import, otherwise 0
 */
public record ChangeRecord(
        String id,
        Instant at,
        String user,
        ChangeOperation operation,
        String listId,
        String listName,
        List<String> valueIds,
        int added,
        int updated) {

    public ChangeRecord {
        valueIds = List.copyOf(valueIds);
    }
}
