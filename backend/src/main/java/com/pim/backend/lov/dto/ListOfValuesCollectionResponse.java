package com.pim.backend.lov.dto;

import com.pim.backend.lov.model.ListSummary;
import java.util.List;

/** Collection envelope of lists (contract {@code ListOfValuesCollection}; ADR 0001). */
public record ListOfValuesCollectionResponse(List<ListOfValuesSummaryResponse> items, int total) {

    public static ListOfValuesCollectionResponse of(List<ListSummary> summaries) {
        List<ListOfValuesSummaryResponse> items =
                summaries.stream().map(ListOfValuesSummaryResponse::from).toList();
        return new ListOfValuesCollectionResponse(items, items.size());
    }
}
