package com.pim.backend.lov.dto;

import com.pim.backend.lov.model.BulkLoadResult;
import java.util.List;

/** What an applied file changed (feature 013 contract {@code BulkLoadResult}). */
public record BulkLoadResultResponse(
        List<PlannedListChangeResponse> lists, int listsCreated, int listsUpdated, int valuesAdded, int valuesUpdated) {

    public static BulkLoadResultResponse from(BulkLoadResult result) {
        return new BulkLoadResultResponse(
                result.lists().stream().map(PlannedListChangeResponse::from).toList(),
                result.listsCreated(),
                result.listsUpdated(),
                result.valuesAdded(),
                result.valuesUpdated());
    }
}
