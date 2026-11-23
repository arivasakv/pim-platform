package com.pim.backend.lov.dto;

import com.pim.backend.lov.model.BulkLoadSummary;
import java.util.List;

/** What a checked file would change; nothing was saved (feature 013 contract {@code BulkLoadSummary}). */
public record BulkLoadSummaryResponse(
        List<PlannedListChangeResponse> lists,
        int listsToCreate,
        int listsToUpdate,
        int valuesToAdd,
        int valuesToUpdate,
        String checkToken) {

    public static BulkLoadSummaryResponse from(BulkLoadSummary summary) {
        return new BulkLoadSummaryResponse(
                summary.lists().stream().map(PlannedListChangeResponse::from).toList(),
                summary.listsToCreate(),
                summary.listsToUpdate(),
                summary.valuesToAdd(),
                summary.valuesToUpdate(),
                summary.checkToken());
    }
}
