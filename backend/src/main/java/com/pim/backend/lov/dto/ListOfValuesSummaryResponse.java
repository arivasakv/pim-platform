package com.pim.backend.lov.dto;

import com.pim.backend.lov.model.ListSummary;

/** One list in the collection (contract {@code ListOfValuesSummary}). */
public record ListOfValuesSummaryResponse(String systemId, String name, int valueCount, int attributeCount) {

    public static ListOfValuesSummaryResponse from(ListSummary summary) {
        return new ListOfValuesSummaryResponse(
                summary.list().systemId(),
                summary.list().name().value(),
                summary.list().values().size(),
                summary.attributeCount());
    }
}
